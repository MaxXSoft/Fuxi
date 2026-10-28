package core.integration

import chisel3._
import chisel3.util.experimental.BoringUtils
import consts.PerfEvent
import consts.PerfEvent._
import consts.PerfEvent.{Retired => R, Microarchitecture => U}
import io.PerformanceEventsIO
import sim.ROM
import utils.{CoreMemoryHarness, CoreTester, ExpectedRetirement, RetirementChecker, TestDriver}

private class PerformanceHarness(ras: Boolean, depth: Int, fenceFault: Boolean)
    extends CoreMemoryHarness(ROM.Words(if (ras) RasProgram.words else RetirementProgram.words),
                              depth, fenceFault) {
  val pmu = IO(Output(new PerformanceEventsIO))
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
  val totals = PerfEvent.groups.map { case (group, events) =>
    group -> Array.fill[Long](events.values.size)(0)
  }.toMap
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
    val bits = PerfEvent.groups.map { case (group, events) =>
      group -> events.values.toSeq.map(e => peek(c.pmu(group)(e.id)) != 0)
    }.toMap
    def retiredEvent(event: R.Value): Boolean = bits(Group.RETIRED)(event.id)
    def microEvent(event: U.Value): Boolean = bits(Group.MICROARCHITECTURE)(event.id)
    for ((group, events) <- bits; id <- events.indices)
      if (events(id)) totals(group)(id) += 1
    val retired = peek(c.io.observation.retired) != 0
    val pc = peek(c.io.observation.debug.pc)
    val branch = retired && pc == (if (ras) RasProgram.branch else RetirementProgram.pcAtWord(9))
    val jal = retired && ras && Set(RasProgram.firstCall, RasProgram.secondCall, BigInt(0x304))(pc)
    val jalr = retired && ras && Set(BigInt(0x308), BigInt(0x344))(pc)
    val load = retired && pc == (if (ras) RasProgram.loopPc else RetirementProgram.pcAtWord(4))
    val store = retired && pc == (if (ras) BigInt(0x204) else RetirementProgram.pcAtWord(3))
    val compressed = retired && ras && Set(RasProgram.decrement, BigInt(0x302),
                                            BigInt(0x308), BigInt(0x342), BigInt(0x344))(pc)
    for ((id, wanted) <- Seq(R.BRANCH -> branch, R.JAL -> jal, R.JALR -> jalr,
                             R.RAS_CANDIDATE -> jalr, R.COMPRESSED -> compressed,
                             R.LOAD -> load, R.STORE -> store)) {
      assert(retiredEvent(id) == wanted, s"Event $id at PC 0x${pc.toString(16)}, retired=$retired")
    }
    assert(!retiredEvent(R.BRANCH_MISS) || branch)
    assert(retiredEvent(R.BRANCH_MISS) == (retiredEvent(R.BRANCH_DIRECTION_MISS) || retiredEvent(R.BRANCH_TARGET_MISS)))
    assert(!(retiredEvent(R.BRANCH_DIRECTION_MISS) && retiredEvent(R.BRANCH_TARGET_MISS)))
    assert(!retiredEvent(R.RAS_USED) || jalr)
    assert(!retiredEvent(R.RAS_MISS) || retiredEvent(R.RAS_USED))
    assert(!retiredEvent(R.RAS_FALLBACK_MISS) || (jalr && !retiredEvent(R.RAS_USED)))
    assert(retiredEvent(R.JALR_MISS) == (retiredEvent(R.RAS_MISS) || retiredEvent(R.RAS_FALLBACK_MISS)))
    assert(!retiredEvent(R.OTHER_JALR) && !retiredEvent(R.OTHER_JALR_MISS))
    assert(!(microEvent(U.RAS_CLEAR_MISPREDICT) && microEvent(U.RAS_CLEAR_CONTEXT)))
    var done = false
    if (retired) {
      checker.observe(sampleDebug(c.io.observation.debug))
      done = pc == donePc
    }
    step(1)
    done
  }
  checker.checkComplete()
  val retiredTotals = totals(Group.RETIRED)
  val microTotals = totals(Group.MICROARCHITECTURE)
  assert(retiredTotals(R.BRANCH.id) == (if (ras) 8 else 1))
  assert(microTotals(U.MEM_STALL.id) >= 12)
  assert(microTotals(U.DECODE_REDIRECT.id) > 0)
  if (ras) {
    assert(retiredTotals(R.JAL.id) == 32 && retiredTotals(R.JALR.id) == 32)
    assert(retiredTotals(R.RAS_USED.id) > 0 && retiredTotals(R.RAS_USED.id) <= retiredTotals(R.RAS_CANDIDATE.id))
    assert(microTotals(U.RAS_CLEAR_MISPREDICT.id) > 0)
  } else {
    assert(microTotals(U.MDU_STALL.id) > 0 && microTotals(U.LOAD_USE_STALL.id) > 0)
    assert(microTotals(U.CONTEXT_FLUSH.id) >= 3)
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
