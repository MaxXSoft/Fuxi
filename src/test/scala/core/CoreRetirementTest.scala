package core

import sim.ROM
import utils.TestDriver

object RetirementProgram extends CoreProgram {
  emitAll(Seq[Long](
    0x30000293, // li t0, trap handler at 0x300
    0x30529073, // csrw mtvec, t0
    0x02a00293, // li t0, 42
    0x00502023, // sw t0, 0(zero)
    0x00002303, // lw t1, 0(zero)
    0x006303b3, // add t2, t1, t1 (load-use bubbles)
    0x00000013, // architectural nop
    0x00300e13, // li t3, 3
    0x03c3ceb3, // div t4, t2, t3 (multicycle stall)
    0x01de8463, // beq t4, t4, +8
    0x06300413, // squashed addi
    0x00000013, // nop at branch target
    0x0000100f, // fence.i
    0x12000073, // sfence.vma
    0x00000073, // ecall: does not retire
  ))
  val donePc = finish()
  seek(64)
  emitAll(Seq[Long](
    0x341022f3, // csrr t0, mepc
    0x00428293, // addi t0, t0, 4
    0x34129073, // csrw mepc, t0
    0x30200073, // mret: retires
  ))
  def pcs(fenceFault: Boolean): Seq[BigInt] = {
    val handler = Seq(64, 65, 66, 67)
    val fences = if (fenceFault) handler ++ handler else Seq(12, 13)
    ((0 to 9) ++ Seq(11) ++ fences ++ handler ++ Seq(15))
      .map(pcAt)
  }
}

class CoreRetirementTester(c: CoreMemoryHarness, fenceFault: Boolean) extends CoreTester(c) {
  val checker = new RetirementChecker(RetirementProgram.pcs(fenceFault).map(ExpectedRetirement(_)))
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)
  runUntil(600, s"Missing retirements: ${checker.missing}") {
    checker.checkCount(peek(c.io.observation.count))
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      checker.observe(actual)
      done = actual.pc == RetirementProgram.donePc
    }
    step(1)
    done
  }
  checker.checkComplete()
  checker.checkCount(peek(c.io.observation.count))
}

object CoreRetirementTest extends App {
  for (fenceFault <- Seq(false, true)) {
    if (!TestDriver.execute(args, () => new CoreMemoryHarness(ROM.Words(RetirementProgram.words), fenceFault = fenceFault))(
      c => new CoreRetirementTester(c, fenceFault))) sys.exit(1)
  }
}
