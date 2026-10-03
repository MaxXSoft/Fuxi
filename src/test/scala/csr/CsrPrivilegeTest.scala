package csr

import chisel3._
import consts.CSR._
import consts.Parameters.HPM_COUNT
import consts.CsrOp._
import utils.{CsrRegressionTester, TestDriver}

class CsrPrivilegeTester(c: CsrFile) extends CsrRegressionTester(c) {
  resetCsr()
  // OpenSBI clears MDT through MSTATUSH before feature probing and on trap
  // entry. Unsupported high-half features read zero but the CSR must exist.
  val mstatush = CSR_MSTATUSH
  read(mstatush, 0)
  for (op <- Seq(CSR_W, CSR_RW, CSR_RS, CSR_RC)) {
    poke(c.io.read.op, op)
    expect(c.io.read.valid, true)
    write(mstatush, 0xffffffffL, op)
    poke(c.io.read.op, CSR_R)
    read(mstatush, 0)
  }

  for (mode <- Seq(CSR_MODE_S, CSR_MODE_U)) {
    resetCsr()
    enterMode(mode)
    poke(c.io.read.addr, mstatush)
    for (op <- Seq(CSR_R, CSR_W, CSR_RW, CSR_RS, CSR_RC)) {
      poke(c.io.read.op, op)
      expect(c.io.read.valid, false)
    }
  }

  val mprv = BigInt(1) << 17
  val sum = BigInt(1) << 18
  val mxr = BigInt(1) << 19
  for (paging <- Seq(false, true); effective <- Seq(CSR_MODE_U, CSR_MODE_S, CSR_MODE_M);
       overrideMode <- Seq(false, true)) {
    resetCsr()
    write(CSR_SATP, (if (paging) BigInt(1) << 31 else BigInt(0)) | 0x123)
    write(CSR_MSTATUS, (effective.litValue << 11) | sum | mxr |
      (if (overrideMode) mprv else BigInt(0)))
    expect(c.io.mode, CSR_MODE_M)
    expect(c.io.pageEn, false) // Fetch never inherits MPRV.
    expect(c.io.dataMode, if (overrideMode) effective else CSR_MODE_M)
    expect(c.io.dataPageEn, paging && overrideMode && effective.litValue != 3)
    expect(c.io.mxr, true)
    expect(c.io.sum, true)
    read(CSR_SSTATUS, sum | mxr)
    write(CSR_SSTATUS, mxr, CSR_RC)
    expect(c.io.mxr, false)
    // The supervisor alias must preserve MPRV and MPP.
    read(CSR_MSTATUS, (effective.litValue << 11) | sum |
      (if (overrideMode) mprv else BigInt(0)))
  }

  // xRET clears MPRV only when returning below M, while MPP resets to U.
  for (target <- Seq(CSR_MODE_U, CSR_MODE_S, CSR_MODE_M)) {
    resetCsr()
    write(CSR_MSTATUS, (target.litValue << 11) | mprv | mxr)
    poke(c.io.except.hasTrap, true)
    poke(c.io.except.isMret, true)
    step(1)
    poke(c.io.except.hasTrap, false)
    poke(c.io.except.isMret, false)
    expect(c.io.mode, target)
    // Read data remains observable to the fixture even when the CSR is not
    // architecturally accessible at the resulting privilege.
    poke(c.io.read.addr, CSR_MSTATUS)
    expect(c.io.read.data, mxr | 0x80 | (if (target.litValue == 3) mprv else BigInt(0)))
  }
  for (targetS <- Seq(false, true)) {
    resetCsr()
    write(CSR_MSTATUS, mprv | mxr | (if (targetS) BigInt(0x100) else BigInt(0)))
    poke(c.io.except.hasTrap, true)
    poke(c.io.except.isSret, true)
    step(1)
    poke(c.io.except.hasTrap, false)
    poke(c.io.except.isSret, false)
    expect(c.io.mode, if (targetS) CSR_MODE_S else CSR_MODE_U)
    poke(c.io.read.addr, CSR_MSTATUS)
    expect(c.io.read.data, mxr | 0x20)
  }
  resetCsr()
  write(CSR_MSTATUS, BigInt(2) << 11) // Reserved MPP maps to an implemented mode.
  read(CSR_MSTATUS, 0)
}

object CsrPrivilegeTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile(HPM_COUNT))(c => new CsrPrivilegeTester(c))) sys.exit(1)
}
