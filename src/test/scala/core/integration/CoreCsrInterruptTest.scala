package core.integration

import utils.DefaultTestConfig._
import chisel3._
import consts.CSR._
import sim.ROM
import utils.InstEncoding._
import utils.{CoreMemoryHarness, CoreTester, CoreTraceProgram, RetirementChecker, TestDriver}

private class CsrInterruptProgram(enableGlobal: Boolean) extends CoreTraceProgram {
  word(addi(5, 0, 0x300))
  word(csrw(CSR_MTVEC.litValue.toInt, 5))
  word(lui(6, 0x10000))
  word(addi(7, 0, 0x55))
  word(addi(5, 0, 128))
  // The timer is pending throughout. Enable its final interrupt condition
  // through either mstatus.MIE or mie.MTIE immediately before an MMIO store.
  if (enableGlobal) {
    word(csrw(CSR_MIE.litValue.toInt, 5))
    word(csrrsi(0, CSR_MSTATUS.litValue.toInt, 8))
  } else {
    word(csrrsi(0, CSR_MSTATUS.litValue.toInt, 8))
    word(csrw(CSR_MIE.litValue.toInt, 5))
  }
  val interruptedPc = word(sw(7, 6, 0), retires = false)
  word(jal(0, 0), retires = false)

  seekAddress(0x300)
  word(csrr(10, CSR_MEPC.litValue.toInt), Some(10 -> interruptedPc))
  word(csrr(11, CSR_MCAUSE.litValue.toInt), Some(11 -> BigInt("80000007", 16)))
  finish()
}

private class CsrInterruptHarness(program: CsrInterruptProgram)
    extends CoreMemoryHarness(ROM.Words(program.words)) {
  core.io.irq.timer := true.B
}

private class CsrInterruptTester(c: CsrInterruptHarness, program: CsrInterruptProgram)
    extends CoreTester(c) {
  val retired = new RetirementChecker(program.retired.toVector)
  var trapCount = 0
  // Never complete a memory request: a spuriously started store would both
  // escape to the device and indefinitely postpone interrupt delivery.
  poke(c.io.memoryStall, true)
  poke(c.io.fenceStall, false)
  runUntil(400, s"Missing CSR interrupt retirements: ${retired.missing}") {
    expect(c.io.memoryRequest, false)
    if (peek(c.io.observation.trap) != 0) {
      expect(c.io.observation.trapPc, program.interruptedPc)
      trapCount += 1
    }
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      retired.observe(actual)
      done = actual.pc == program.donePc
    }
    step(1)
    done
  }
  retired.checkComplete()
  assert(trapCount == 1, s"Expected one timer interrupt, observed $trapCount")
}

object CoreCsrInterruptTest extends App {
  for (enableGlobal <- Seq(true, false)) {
    val program = new CsrInterruptProgram(enableGlobal)
    if (!TestDriver.execute(args, () => new CsrInterruptHarness(program))(
      c => new CsrInterruptTester(c, program))) sys.exit(1)
  }
}
