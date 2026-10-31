package csr

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import configs.{CoreParamsKey, PlatformParamsKey}
import chisel3._
import consts.CSR._
import consts.CsrOp._
import utils.{CsrRegressionTester, TestDriver}

class CsrPendingTester(c: CsrFile) extends CsrRegressionTester(c) {
  // External SEIP contributes to the CSR read result, but never to the
  // software bit used by CSRRS/CSRRC, including their immediate variants.
  for (op <- Seq(CSR_RS, CSR_RC); software <- Seq(0, 0x200)) {
    resetCsr()
    write(CSR_MIP, software)
    poke(c.io.irq.supervisorExtern, true)
    read(CSR_MIP, 0x200)
    write(CSR_MIP, 2, op)
    read(CSR_MIP, 0x200 | (if (op == CSR_RS) 2 else 0))
    poke(c.io.irq.supervisorExtern, false)
    read(CSR_MIP, software | (if (op == CSR_RS) 2 else 0))
  }

  // Explicit writes to SEIP still change the software-pending state even
  // while the hardware source is asserted.
  resetCsr()
  poke(c.io.irq.supervisorExtern, true)
  write(CSR_MIP, 0x200, CSR_RS)
  poke(c.io.irq.supervisorExtern, false)
  read(CSR_MIP, 0x200)
  poke(c.io.irq.supervisorExtern, true)
  write(CSR_MIP, 0x200, CSR_RC)
  read(CSR_MIP, 0x200)
  poke(c.io.irq.supervisorExtern, false)
  read(CSR_MIP, 0)
}

class CsrPendingTest extends AnyFunSuite {
  test("update pending interrupt state") {
    TestDriver.run(new CsrFile(p(CoreParamsKey).hpmCounters, p(PlatformParamsKey).hartId)) { c =>
      new CsrPendingTester(c)
    }
  }
}
