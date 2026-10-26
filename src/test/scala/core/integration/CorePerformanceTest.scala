package core.integration

import chisel3._
import chisel3.util.experimental.BoringUtils
import consts.PerfEvent
import consts.PerfEvent._
import sim.ROM
import utils.{CoreMemoryHarness, CoreTester, ExpectedRetirement, RetirementChecker, TestDriver}

private class PerformanceHarness(ras: Boolean, depth: Int, fenceFault: Boolean)
    extends CoreMemoryHarness(ROM.Words(if (ras) RasProgram.words else RetirementProgram.words),
                              depth, fenceFault) {
  val pmu = IO(Output(UInt(PerfEvent.values.size.W)))
  pmu := BoringUtils.bore(core.csrfile.io.perfEvents)
}

// Compare events against an independently specified dynamic instruction trace.
// Memory/fence waits fill the queue; redirects, synchronous traps, and failed
// fences discard instructions whose metadata must never become retired events.
private class PerformanceTester(c: PerformanceHarness, ras: Boolean, fenceFault: Boolean)
    extends CoreTester(c) {
  val expected = if (ras) RasProgram.retired.toVector
                 else RetirementProgram.pcs(fenceFault).map(ExpectedRetirement(_))
  val checker = new RetirementChecker(expected)
  val donePc = if (ras) RasProgram.donePc else RetirementProgram.donePc
  val totals = Array.fill[Long](PerfEvent.values.size)(0)
  var memoryWait = 0
  var memoryArmed = true
  var fenceWait = 0
  var fenceArmed = true
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)
  runUntil(6000, s"Missing PMU retirements: ${checker.missing}") {
    if (peek(c.io.memoryRequest) == 0) memoryArmed = true
    else if (memoryArmed) { memoryWait = 12; memoryArmed = false }
    if (peek(c.io.fenceRequest) == 0) fenceArmed = true
    else if (fenceArmed) { fenceWait = 9; fenceArmed = false }
    poke(c.io.memoryStall, memoryWait > 0)
    poke(c.io.fenceStall, fenceWait > 0)
    memoryWait = math.max(0, memoryWait - 1)
    fenceWait = math.max(0, fenceWait - 1)
    val bits = peek(c.pmu)
    def event(id: Int): Boolean = bits.testBit(id)
    for (id <- 1 until totals.length) if (event(id)) totals(id) += 1
    val retired = peek(c.io.observation.retired) != 0
    val pc = peek(c.io.observation.debug.pc)
    val branch = retired && pc == (if (ras) RasProgram.branch else RetirementProgram.pcAtWord(9))
    val jal = retired && ras && Set(RasProgram.firstCall, RasProgram.secondCall, BigInt(0x304))(pc)
    val jalr = retired && ras && Set(BigInt(0x308), BigInt(0x344))(pc)
    val load = retired && pc == (if (ras) RasProgram.loopPc else RetirementProgram.pcAtWord(4))
    val store = retired && pc == (if (ras) BigInt(0x204) else RetirementProgram.pcAtWord(3))
    val compressed = retired && ras && Set(RasProgram.decrement, BigInt(0x302),
                                            BigInt(0x308), BigInt(0x342), BigInt(0x344))(pc)
    for ((id, wanted) <- Seq(BRANCH -> branch, JAL -> jal, JALR -> jalr,
                             RAS_CANDIDATE -> jalr, COMPRESSED -> compressed,
                             LOAD -> load, STORE -> store))
      assert(event(id.id) == wanted, s"Event $id at PC 0x${pc.toString(16)}, retired=$retired")
    assert(!event(BRANCH_MISS.id) || branch)
    assert(event(BRANCH_MISS.id) == (event(BRANCH_DIRECTION_MISS.id) || event(BRANCH_TARGET_MISS.id)))
    assert(!(event(BRANCH_DIRECTION_MISS.id) && event(BRANCH_TARGET_MISS.id)))
    assert(!event(RAS_USED.id) || jalr)
    assert(!event(RAS_MISS.id) || event(RAS_USED.id))
    assert(!event(RAS_FALLBACK_MISS.id) || (jalr && !event(RAS_USED.id)))
    assert(event(JALR_MISS.id) == (event(RAS_MISS.id) || event(RAS_FALLBACK_MISS.id)))
    assert(!event(OTHER_JALR.id) && !event(OTHER_JALR_MISS.id))
    assert(!(event(RAS_CLEAR_MISPREDICT.id) && event(RAS_CLEAR_CONTEXT.id)))
    var done = false
    if (retired) {
      checker.observe(sampleDebug(c.io.observation.debug))
      done = pc == donePc
    }
    step(1)
    done
  }
  checker.checkComplete()
  assert(totals(BRANCH.id) == (if (ras) 8 else 1))
  assert(totals(MEM_STALL.id) >= 12)
  assert(totals(DECODE_REDIRECT.id) > 0)
  if (ras) {
    assert(totals(JAL.id) == 32 && totals(JALR.id) == 32)
    assert(totals(RAS_USED.id) > 0 && totals(RAS_USED.id) <= totals(RAS_CANDIDATE.id))
    assert(totals(RAS_CLEAR_MISPREDICT.id) > 0)
  } else {
    assert(totals(MDU_STALL.id) > 0 && totals(LOAD_USE_STALL.id) > 0)
    assert(totals(CONTEXT_FLUSH.id) >= 3)
  }
}

object CorePerformanceTest extends App {
  for ((ras, depth, fenceFault) <- Seq((true, 1, false), (true, 4, false),
                                      (false, 4, false), (false, 4, true))) {
    if (!TestDriver.execute(args, () => new PerformanceHarness(ras, depth, fenceFault)) {
      c => new PerformanceTester(c, ras, fenceFault)
    }) sys.exit(1)
  }
}
