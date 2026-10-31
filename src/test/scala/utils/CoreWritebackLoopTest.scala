package utils

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import InstEncoding._

class WritebackLoopProgram extends CoreProgram {
  emit32(addi(10, 0, 0))
  emit32(addi(11, 0, 6))
  val loopPc = pc
  expectWriteback(10, 2, occurrence = 2)
  expectWriteback(10, 5, occurrence = 5)
  emit32(addi(10, 10, 1))
  emit32(blt(10, 11, (loopPc - pc).toInt))
  val donePc = finish()
}

class CoreWritebackLoopTest extends AnyFunSuite {
  test("check repeated writeback events and occurrence counts") {
    val program = new WritebackLoopProgram
    TestDriver.run(new CoreWrapper(program.words)) { c =>
      new CoreProgramTester(c, program, program.donePc, 200) {
        // Selected values do not imply the loop's total iteration count.
        checker.checkOccurrences(program.loopPc, 6)
      }
    }
  }
}
