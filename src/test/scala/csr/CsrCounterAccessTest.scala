package csr

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import configs.{CoreParamsKey, PlatformParamsKey}
import chisel3._
import consts.CSR._
import consts.CsrOp._
import utils.{CsrRegressionTester, TestDriver}

class CsrCounterAccessTester(c: CsrFile) extends CsrRegressionTester(c) {
  val counters = Seq(CSR_CYCLE, CSR_CYCLEH, CSR_TIME, CSR_TIMEH, CSR_INSTRET, CSR_INSTRETH,
                     hpmCounterAddr(3), hpmCounterHighAddr(3), hpmCounterAddr(10),
                     hpmCounterHighAddr(10))
  // PMP remains WARL zero across the complete currently exposed RV32 range.
  resetCsr()
  for (address <- (0x3a0 to 0x3a3) ++ (0x3b0 to 0x3bf)) {
    val addr = address.U(12.W)
    read(addr, 0)
    poke(c.io.read.op, CSR_RW)
    expect(c.io.read.valid, true)
    write(addr, 0xffffffffL)
    read(addr, 0)
  }
  poke(c.io.read.op, CSR_R)
  for (address <- Seq(0x3a4, 0x3af, 0x3c0)) {
    poke(c.io.read.addr, address)
    expect(c.io.read.valid, false)
  }

  val supportedMask = BigInt("7ff", 16)
  for (mode <- Seq(CSR_MODE_M, CSR_MODE_S, CSR_MODE_U);
       machineEnable <- Seq(BigInt(0), BigInt(2), BigInt(9), supportedMask);
       supervisorEnable <- Seq(BigInt(0), BigInt(2), BigInt(12), supportedMask)) {
    resetCsr()
    write(CSR_MCOUNTEREN, machineEnable)
    write(CSR_SCOUNTEREN, supervisorEnable)
    if (mode.litValue != CSR_MODE_M.litValue) enterMode(mode)
    for (counter <- counters) {
      val bit = counter.litValue.toInt & 31
      val allowed = mode.litValue == CSR_MODE_M.litValue ||
        (machineEnable.testBit(bit) &&
          (mode.litValue != CSR_MODE_U.litValue || supervisorEnable.testBit(bit)))
      poke(c.io.read.addr, counter)
      poke(c.io.read.op, CSR_R)
      expect(c.io.read.valid, allowed)
      for (op <- Seq(CSR_W, CSR_RW, CSR_RS, CSR_RC)) {
        poke(c.io.read.op, op)
        expect(c.io.read.valid, false)
      }
    }
    poke(c.io.read.op, CSR_R)
    // Machine aliases and selectors remain privileged even when user aliases
    // have been enabled through the two counter access-control registers.
    for (counter <- Seq(CSR_MCYCLE, CSR_MCYCLEH, CSR_MINSTRET, CSR_MINSTRETH,
                        mhpmCounterAddr(3), mhpmCounterHighAddr(3), mhpmEventAddr(3))) {
      poke(c.io.read.addr, counter)
      expect(c.io.read.valid, mode.litValue == CSR_MODE_M.litValue)
    }
  }
  resetCsr()
  for (enable <- Seq(CSR_MCOUNTEREN, CSR_SCOUNTEREN)) {
    write(enable, 0xffffffffL)
    read(enable, supportedMask)
    write(enable, 8, CSR_RC)
    read(enable, supportedMask & ~BigInt(8))
    write(enable, 8, CSR_RS)
    read(enable, supportedMask)
  }
  // Absent HPM slots have hardwired-zero enable bits, so lower privilege
  // cannot access their user aliases even after writing every enable bit.
  for (mode <- Seq(CSR_MODE_S, CSR_MODE_U)) {
    resetCsr()
    write(CSR_MCOUNTEREN, 0xffffffffL)
    write(CSR_SCOUNTEREN, 0xffffffffL)
    enterMode(mode)
    for (counter <- Seq(hpmCounterAddr(11), hpmCounterHighAddr(31))) {
      poke(c.io.read.addr, counter)
      expect(c.io.read.valid, false)
    }
  }
}

class CsrCounterAccessTest extends AnyFunSuite {
  test("enforce counter access permissions") {
    TestDriver.run(new CsrFile(p(CoreParamsKey).hpmCounters, p(PlatformParamsKey).hartId)) { c =>
      new CsrCounterAccessTester(c)
    }
  }
}
