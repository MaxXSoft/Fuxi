package csr

import chisel3._
import consts.CSR._
import consts.CsrOp._
import utils.TestDriver

class CsrCounterAccessTester(c: CsrFile) extends CsrRegressionTester(c) {
  val counters = Seq(CSR_CYCLE, CSR_CYCLEH, CSR_INSTRET, CSR_INSTRETH)
  for (mode <- Seq(CSR_MODE_M, CSR_MODE_S, CSR_MODE_U)) {
    resetCsr()
    // Both enable CSRs are implemented as WARL zero. Attempts to enable
    // lower-privilege access cannot grant permissions that are not stored.
    write(CSR_MCOUNTEREN, 0xffffffffL)
    write(CSR_SCOUNTEREN, 0xffffffffL)
    read(CSR_MCOUNTEREN, 0)
    read(CSR_SCOUNTEREN, 0)
    if (mode.litValue != CSR_MODE_M.litValue) enterMode(mode)
    for (counter <- counters) {
      poke(c.io.read.addr, counter)
      poke(c.io.read.op, CSR_R)
      expect(c.io.read.valid, mode.litValue == CSR_MODE_M.litValue)
    }
    // Machine aliases remain protected by their CSR address privilege.
    for (counter <- Seq(CSR_MCYCLE, CSR_MCYCLEH, CSR_MINSTRET, CSR_MINSTRETH)) {
      poke(c.io.read.addr, counter)
      expect(c.io.read.valid, mode.litValue == CSR_MODE_M.litValue)
    }
  }
}

object CsrCounterAccessTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile)(c => new CsrCounterAccessTester(c))) sys.exit(1)
}
