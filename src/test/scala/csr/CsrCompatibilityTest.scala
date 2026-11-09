package csr

import chisel3._
import consts.CSR._
import consts.CsrOp._
import org.scalatest.funsuite.AnyFunSuite
import utils.{CsrRegressionTester, TestDriver}

class CsrCompatibilityTester(c: CsrFile) extends CsrRegressionTester(c) {
  resetCsr()
  read(CSR_MCONFIGPTR, 0)
  for (op <- Seq(CSR_W, CSR_RW, CSR_RS, CSR_RC)) {
    poke(c.io.read.op, op)
    expect(c.io.read.valid, false) // Read-only, even when the write value is zero.
  }
  poke(c.io.read.op, CSR_R)
  write(CSR_MEDELEG, 0x100)
  read(CSR_MEDELEGH, 0)
  for (op <- Seq(CSR_W, CSR_RW, CSR_RS, CSR_RC)) {
    poke(c.io.read.op, op)
    expect(c.io.read.valid, true)
    write(CSR_MEDELEGH, 0xffffffffL, op)
    poke(c.io.read.op, CSR_R)
    read(CSR_MEDELEGH, 0)
  }
  read(CSR_MEDELEG, 0x100) // High-half writes must not change the low half.

  for (mode <- Seq(CSR_MODE_S, CSR_MODE_U)) {
    resetCsr()
    enterMode(mode)
    for (addr <- Seq(CSR_MCONFIGPTR, CSR_MEDELEGH);
         op <- Seq(CSR_R, CSR_W, CSR_RW, CSR_RS, CSR_RC)) {
      poke(c.io.read.addr, addr)
      poke(c.io.read.op, op)
      expect(c.io.read.valid, false)
    }
  }

  // The minimum cause-code width applies to explicit writes, independently
  // of which exception/interrupt sources the hart can generate.
  for (mode <- Seq(CSR_MODE_M, CSR_MODE_S)) {
    resetCsr()
    if (mode == CSR_MODE_S) enterMode(mode)
    for (interrupt <- Seq(BigInt(0), BigInt(1) << 31); code <- 0 until 32) {
      val value = interrupt | code
      write(CSR_SCAUSE, value)
      read(CSR_SCAUSE, value)
    }
    write(CSR_SCAUSE, 0)
    write(CSR_SCAUSE, 16, CSR_RS)
    read(CSR_SCAUSE, 16)
    write(CSR_SCAUSE, 16, CSR_RC)
    read(CSR_SCAUSE, 0)
  }

  resetCsr()
  val tw = BigInt(1) << 21
  write(CSR_MSTATUS, tw)
  read(CSR_MSTATUS, tw)
  write(CSR_SSTATUS, 0xffffffffL)
  poke(c.io.read.addr, CSR_MSTATUS)
  assert((peek(c.io.read.data) & tw) != 0, "sstatus changed TW")
  poke(c.io.read.addr, CSR_SSTATUS)
  assert((peek(c.io.read.data) & tw) == 0, "sstatus exposed TW")
  write(CSR_MSTATUS, 0)
  write(CSR_MSTATUS, tw, CSR_RS)
  read(CSR_MSTATUS, tw)
  write(CSR_MSTATUS, tw, CSR_RC)
  read(CSR_MSTATUS, 0)
}

class CsrCompatibilityTest extends AnyFunSuite {
  test("implement zero CSRs, five-bit scause and writable TW with correct access rules") {
    TestDriver.run(new CsrFile(0, 0))(c => new CsrCompatibilityTester(c))
  }
}
