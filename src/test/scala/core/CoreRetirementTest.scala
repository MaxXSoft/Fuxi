package core

import core.InstEncoding._
import sim.ROM
import utils.TestDriver

object RetirementProgram extends CoreProgram {
  emit32All(Seq[Long](
    addi(5, 0, 0x300), // li t0, trap handler at 0x300
    csrw(0x305, 5), // csrw mtvec, t0
    addi(5, 0, 42), // li t0, 42
    sw(5, 0, 0), // sw t0, 0(zero)
    lw(6, 0, 0), // lw t1, 0(zero)
    add(7, 6, 6), // add t2, t1, t1 (load-use bubbles)
    nop(), // architectural nop
    addi(28, 0, 3), // li t3, 3
    div(29, 7, 28), // div t4, t2, t3 (multicycle stall)
    beq(29, 29, 8), // beq t4, t4, +8
    addi(8, 0, 99), // squashed addi
    nop(), // nop at branch target
    fence_i(), // fence.i
    sfence_vma(), // sfence.vma
    ecall(), // ecall: does not retire
  ))
  val donePc = finish()
  seekWord(64)
  emit32All(Seq[Long](
    csrr(5, 0x341), // csrr t0, mepc
    addi(5, 5, 4), // addi t0, t0, 4
    csrw(0x341, 5), // csrw mepc, t0
    mret(), // mret: retires
  ))
  def pcs(fenceFault: Boolean): Seq[BigInt] = {
    val handler = Seq(64, 65, 66, 67)
    val fences = if (fenceFault) handler ++ handler else Seq(12, 13)
    ((0 to 9) ++ Seq(11) ++ fences ++ handler ++ Seq(15))
      .map(pcAtWord)
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
