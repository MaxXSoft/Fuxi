package csr

import chisel3._
import consts.CSR._
import consts.CsrOp._
import utils.TestDriver

class CsrPendingTester(c: CsrFile) extends CsrRegressionTester(c) {
  // External SEIP contributes to the CSR read result, but never to the
  // software bit used by CSRRS/CSRRC, including their immediate variants.
  for (op <- Seq(CSR_RS, CSR_RC); software <- Seq(0, 0x200)) {
    resetCsr()
    write(CSR_MIP, software)
    poke(c.io.irq.extern, true)
    read(CSR_MIP, 0xa00)
    write(CSR_MIP, 2, op)
    read(CSR_MIP, 0xa00 | (if (op == CSR_RS) 2 else 0))
    poke(c.io.irq.extern, false)
    read(CSR_MIP, software | (if (op == CSR_RS) 2 else 0))
  }

  // Explicit writes to SEIP still change the software-pending state even
  // while the hardware source is asserted.
  resetCsr()
  poke(c.io.irq.extern, true)
  write(CSR_MIP, 0x200, CSR_RS)
  poke(c.io.irq.extern, false)
  read(CSR_MIP, 0x200)
  poke(c.io.irq.extern, true)
  write(CSR_MIP, 0x200, CSR_RC)
  read(CSR_MIP, 0xa00)
  poke(c.io.irq.extern, false)
  read(CSR_MIP, 0)
}

object CsrPendingTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile)(c => new CsrPendingTester(c))) sys.exit(1)
}
