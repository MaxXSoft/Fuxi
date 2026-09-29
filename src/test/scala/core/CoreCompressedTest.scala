package core

import sim.ROM
import utils.TestDriver

private[core] object CompressedFlowProgram extends CoreTraceProgram {
  import RiscvTestEncoding._
  def write(rd: Int, data: BigInt): Option[(Int, BigInt)] = Some(rd -> data)

  half(0x0001) // C.NOP makes the following full-width instruction straddle words.
  word(addi(2, 0, 128), write(2, 128))
  half(ci(2, 10, 5), write(10, 5))
  half(ci(2, 11, 7), write(11, 7))
  half(0x952e, write(10, 12)) // c.add a0, a1
  half(0xc02a) // c.swsp a0, 0(sp)
  half(lwsp(12, 0), write(12, 12))
  half(0x962a, write(12, 24)) // c.add a2, a0: load-use hazard

  private val firstReturn = pc + 2
  half(cj(1, (0x300 - pc).toInt), write(1, firstReturn))
  event(0x300, write(10, 13))
  event(0x302)

  word(addi(5, 0, 0x320), write(5, 0x320))
  word(sw(5, 2, 4))
  half(lwsp(5, 4), write(5, 0x320))
  private val secondReturn = pc + 2
  half(0x9282, write(1, secondReturn)) // c.jalr t0 immediately after the load
  event(0x320, write(13, secondReturn))
  event(0x322)

  word(addi(5, 0, 0x342), write(5, 0x342))
  word(sw(5, 2, 8))
  half(lwsp(5, 8), write(5, 0x342))
  half(0x8282) // c.jr t0 immediately after the load
  private val thirdReturn = pc
  event(0x342, write(12, 25))
  event(0x344)

  // HINTs must retire individually without changing their apparent operands.
  Seq(0x0001, 0x0005, 0x0501, 0x4005, 0x8006, 0x900a, 0x0006, 0x8001)
    .foreach(half(_))
  half(ci(2, 8, 0), write(8, 0))
  half(0xc019) // c.beqz s0, +6
  word(addi(20, 0, 99), retires = false)
  half(ci(0, 10, 1), write(10, 14))
  word(0x0000100f) // FENCE.I while younger packets accumulate in the FIFO.
  half(ci(0, 10, 1), write(10, 15))
  word(0x12000073) // SFENCE.VMA also restarts the younger stream.
  half(ci(0, 10, 1), write(10, 16))
  word(csr(0xc02, 0, 2, 14), write(14, retired.size))
  finish()

  place16(0x300, ci(0, 10, 1))
  place16(0x302, 0x8082)
  place16(0x320, 0x8686) // c.mv a3, ra
  place16(0x322, 0x8082)
  place16(0x342, ci(0, 12, 1))
  place32(0x344, jal(0, (thirdReturn - 0x344).toInt))
}

private[core] object CompressedTrapProgram extends CoreTraceProgram {
  import RiscvTestEncoding._
  def write(rd: Int, data: BigInt): Option[(Int, BigInt)] = Some(rd -> data)
  word(addi(5, 0, 0x380), write(5, 0x380))
  word(csr(0x305, 5))
  for ((instruction, cause) <- Seq(0x0000 -> 2, 0x9c01 -> 2, 0x9002 -> 3, 0x0000 -> 2)) {
    val faultPc = half(instruction, retires = false)
    val value = if (cause == 2) instruction else 0
    traps += ExpectedTrap(faultPc, cause, value)
    event(0x380, write(6, cause))
    event(0x384, write(7, faultPc))
    event(0x388, write(28, value))
    event(0x38c, write(7, faultPc + 2))
    event(0x390)
    event(0x394)
  }
  // SRET must also preserve bit 1 of SEPC; run its target in supervisor mode.
  word(addi(5, 0, 0x362), write(5, 0x362))
  word(csr(0x141, 5))
  word(addi(5, 0, 0x100), write(5, 0x100))
  word(csr(0x100, 5))
  word(0x10200073)
  word(addi(20, 0, 99), retires = false)
  seekAddress(0x362)
  half(ci(2, 10, 9), write(10, 9))
  finish()

  place32(0x380, csr(0x342, 0, 2, 6))
  place32(0x384, csr(0x341, 0, 2, 7))
  place32(0x388, csr(0x343, 0, 2, 28))
  place32(0x38c, addi(7, 7, 2))
  place32(0x390, csr(0x341, 7))
  place32(0x394, 0x30200073)
}

private[core] class CoreCompressedTester(c: CoreMemoryHarness, program: CoreTraceProgram,
                                         depth: Int, stall: Boolean) extends CoreTester(c) {
  val retirements = new RetirementChecker(program.retired.toVector)
  val traps = new TrapChecker(program.traps.toVector)
  var maxQueueCount = 0
  var memoryWait = 0
  var fenceWait = 0
  var memoryArmed = true
  var fenceArmed = true
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)

  runUntil(1800, s"missing retirements ${retirements.missing}; traps ${traps.missing}") {
    if (peek(c.io.memoryRequest) == 0) memoryArmed = true
    else if (memoryArmed && stall) { memoryWait = 8; memoryArmed = false }
    if (peek(c.io.fenceRequest) == 0) fenceArmed = true
    else if (fenceArmed && stall) { fenceWait = 10; fenceArmed = false }
    poke(c.io.memoryStall, memoryWait > 0)
    poke(c.io.fenceStall, fenceWait > 0)
    memoryWait = math.max(0, memoryWait - 1)
    fenceWait = math.max(0, fenceWait - 1)
    maxQueueCount = math.max(maxQueueCount, peek(c.io.queueCount).toInt)

    retirements.checkCount(peek(c.io.observation.count))
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      retirements.observe(actual)
      done = actual.pc == program.donePc
    }
    step(1)
    done
  }
  retirements.checkComplete()
  traps.checkComplete()
  retirements.checkCount(peek(c.io.observation.count))
  if (stall) assert(maxQueueCount == depth, s"FIFO did not fill under forced stalls: $maxQueueCount/$depth")
}

object CoreCompressedTest extends App {
  for ((program, depth, stall) <- Seq(
    (CompressedFlowProgram, 1, true),
    (CompressedFlowProgram, 4, true),
    (CompressedTrapProgram, 4, false),
  )) {
    if (!TestDriver.execute(args, () => new CoreMemoryHarness(ROM.Words(program.words), depth)) {
      c => new CoreCompressedTester(c, program, depth, stall)
    }) sys.exit(1)
  }
}
