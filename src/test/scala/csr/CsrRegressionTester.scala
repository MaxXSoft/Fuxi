package csr

import chisel3._
import consts.CSR._
import consts.CsrOp._
import utils.PeekPokeTester

abstract class CsrRegressionTester(c: CsrFile) extends PeekPokeTester(c) {
  def resetCsr(): Unit = {
    poke(c.io.read.op, CSR_R)
    poke(c.io.read.addr, CSR_MSTATUS)
    poke(c.io.write.op, CSR_NOP)
    poke(c.io.write.addr, 0)
    poke(c.io.write.data, 0)
    poke(c.io.write.retired, false)
    poke(c.io.irq.timer, false)
    poke(c.io.irq.soft, false)
    poke(c.io.irq.extern, false)
    poke(c.io.except.hasTrap, false)
    poke(c.io.except.isInterrupt, false)
    poke(c.io.except.isSret, false)
    poke(c.io.except.isMret, false)
    poke(c.io.except.excCause, 0)
    poke(c.io.except.excPc, 0x1000)
    poke(c.io.except.excValue, 0)
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
  }

  def write(addr: UInt, data: BigInt, op: UInt = CSR_W): Unit = {
    poke(c.io.write.op, op)
    poke(c.io.write.addr, addr)
    poke(c.io.write.data, data)
    step(1)
    poke(c.io.write.op, CSR_NOP)
  }

  def read(addr: UInt, data: BigInt): Unit = {
    poke(c.io.read.addr, addr)
    expect(c.io.read.valid, true)
    expect(c.io.read.data, data)
  }

  def enterMode(mode: UInt): Unit = {
    write(CSR_MSTATUS, mode.litValue << 11)
    poke(c.io.except.hasTrap, true)
    poke(c.io.except.isMret, true)
    step(1)
    poke(c.io.except.hasTrap, false)
    poke(c.io.except.isMret, false)
    expect(c.io.mode, mode)
  }
}
