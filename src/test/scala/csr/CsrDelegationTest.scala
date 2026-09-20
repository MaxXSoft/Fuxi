package csr

import chisel3._
import consts.CSR._
import consts.CsrOp._
import utils.TestDriver

class CsrDelegationTester(c: CsrFile) extends CsrRegressionTester(c) {
  val sources = Seq(1, 5, 9)
  for (bits <- 0 until 8; op <- Seq(CSR_W, CSR_RS, CSR_RC);
       initial <- Seq(0, 0x222); data <- Seq(0, 0x222)) {
    resetCsr()
    val delegated = sources.zipWithIndex.collect {
      case (bit, index) if (bits & (1 << index)) != 0 => 1 << bit
    }.sum
    write(CSR_MIDELEG, delegated)
    write(CSR_MIE, initial | 0x888)
    write(CSR_MIP, initial)
    read(CSR_SIE, initial & delegated)
    read(CSR_SIP, initial & delegated)
    def modified(old: Int): Int =
      if (op.litValue == CSR_W.litValue) data
      else if (op.litValue == CSR_RS.litValue) old | data
      else old & ~data
    write(CSR_SIE, data, op)
    read(CSR_MIE, (initial & ~delegated) | (modified(initial) & delegated) | 0x888)
    write(CSR_SIP, data, op)
    // Only SSIP is writable through SIP, even if SEIP/STIP are delegated.
    val writablePending = delegated & 2
    read(CSR_MIP, (initial & ~writablePending) | (modified(initial) & writablePending))
  }

  // The supervisor view changes immediately when delegation is changed,
  // including pending sources asserted by hardware.
  resetCsr()
  poke(c.io.irq.extern, true)
  poke(c.io.irq.timer, true)
  poke(c.io.irq.soft, true)
  read(CSR_SIP, 0)
  write(CSR_MIDELEG, 0x222)
  read(CSR_SIP, 0x222)
  write(CSR_MIDELEG, 0)
  read(CSR_SIP, 0)
}

object CsrDelegationTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile)(c => new CsrDelegationTester(c))) sys.exit(1)
}
