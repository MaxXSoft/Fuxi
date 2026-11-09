package core.integration

import org.scalatest.funsuite.AnyFunSuite
import utils.DefaultTestConfig._
import utils.InstEncoding._
import utils.{CoreProgram, CoreProgramTester, CoreWrapper, TestDriver}

class WfiProgram(mode: Int, tw: Boolean) extends CoreProgram {
  val status = (if (tw) 1 << 21 else 0) | (mode << 11)
  emit32All(Seq(lui(5, (status + 0x800) >> 12),
    addi(5, 5, (status & 0xfff) - (if ((status & 0x800) != 0) 0x1000 else 0)),
    csrw(0x300, 5)))
  expectWriteback(6, status)
  emit32(csrr(6, 0x300))
  if (mode != 3) {
    val target = (pc + 12).toInt
    emit32All(Seq(addi(5, 0, target), csrw(0x341, 5), mret()))
  }
  emit32(0x10500073L) // WFI completes immediately in M/S/U, for either TW value.
  expectWriteback(10, 7)
  emit32(addi(10, 0, 7))
  val donePc = finish()
}

class CoreWfiTest extends AnyFunSuite {
  test("complete WFI without a timeout trap in every privilege mode and TW setting") {
    for (mode <- Seq(0, 1, 3); tw <- Seq(false, true)) {
      val program = new WfiProgram(mode, tw)
      TestDriver.run(new CoreWrapper(program.words)) { c =>
        new CoreProgramTester(c, program, program.donePc, 100)
      }
    }
  }
}
