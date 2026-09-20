package csr

import chisel3._
import consts.CSR._
import utils.TestDriver

class CsrInterruptTester(c: CsrFile) extends CsrRegressionTester(c) {
  val sources = Seq(9, 1, 5)
  def mask(bits: Int): Int = sources.zipWithIndex.collect {
    case (bit, index) if (bits & (1 << index)) != 0 => 1 << bit
  }.sum

  // Each target's priority encoder must see only sources routed to it.
  for (supervisor <- Seq(false, true); pendingBits <- 1 until 8;
       delegatedBits <- 0 until 8) {
    resetCsr()
    val pending = mask(pendingBits)
    val delegated = mask(delegatedBits)
    write(CSR_MIDELEG, delegated)
    write(CSR_MIE, pending)
    write(CSR_MIP, pending)
    write(CSR_MTVEC, 0x101)
    write(CSR_STVEC, 0x201)
    if (supervisor) {
      enterMode(CSR_MODE_S)
      write(CSR_SSTATUS, 2)
    } else {
      write(CSR_MSTATUS, 8)
    }
    val machinePending = pending & ~delegated
    val targetS = supervisor && machinePending == 0
    val eligible = if (targetS) pending & delegated else machinePending
    expect(c.io.hasInt, eligible != 0)
    if (eligible != 0) {
      val cause = sources.find(bit => (eligible & (1 << bit)) != 0).get
      poke(c.io.except.hasTrap, true)
      poke(c.io.except.isInterrupt, true)
      expect(c.io.trapVec, (if (targetS) 0x200 else 0x100) + 4 * cause)
      step(1)
      poke(c.io.except.hasTrap, false)
      poke(c.io.except.isInterrupt, false)
      expect(c.io.mode, if (targetS) CSR_MODE_S else CSR_MODE_M)
      read(if (targetS) CSR_SCAUSE else CSR_MCAUSE, (BigInt(1) << 31) | cause)
    }
  }

  // Machine external/software/timer sources retain priority over S sources.
  resetCsr()
  write(CSR_MIE, 0xaaa)
  write(CSR_MSTATUS, 8)
  write(CSR_MTVEC, 0x101)
  poke(c.io.irq.extern, true)
  poke(c.io.irq.soft, true)
  poke(c.io.irq.timer, true)
  poke(c.io.except.hasTrap, true)
  poke(c.io.except.isInterrupt, true)
  expect(c.io.trapVec, 0x12c)
  poke(c.io.irq.extern, false)
  expect(c.io.trapVec, 0x10c)
  poke(c.io.irq.soft, false)
  expect(c.io.trapVec, 0x11c)
}

object CsrInterruptTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile)(c => new CsrInterruptTester(c))) sys.exit(1)
}
