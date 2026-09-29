package lsu

import core._
import core.RiscvTestEncoding._
import utils.TestDriver

class ScSequenceProgram(gap: Int, interveningSc: Boolean) extends CoreProgram {
  emitAll(Seq(addi(1, 0, 0x100), addi(2, 0, 0x104), addi(3, 0, 7),
    sw(0, 1, 0), sw(0, 2, 0), lr(4, 1)))
  if (interveningSc) {
    expectWriteback(5, 1)
    emit(sc(5, 3, 2))
  } else emit(sw(3, 2, 0))
  emitAll(Seq.fill(gap)(addi(0, 0, 0)))
  expectWriteback(6, if (interveningSc) 1 else 0)
  emit(sc(6, 3, 1))
  expectWriteback(7, if (interveningSc) 0 else 7)
  emit(0x0000a383) // lw t2, 0(ra)
  val donePc = finish()
}

object ScSequenceTest extends App {
  // Adjacent instructions exercise WB forwarding; gaps exercise committed
  // reservation state. An unrelated ordinary store preserves the reservation.
  for (gap <- Seq(0, 1, 2); interveningSc <- Seq(true, false)) {
    val program = new ScSequenceProgram(gap, interveningSc)
    if (!TestDriver.execute(args, () => new CoreWrapper(program.words)) {
      c => new CoreProgramTester(c, program, program.donePc, 60)
    }) sys.exit(1)
  }
}
