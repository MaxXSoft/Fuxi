package csr

import chisel3.iotesters.{Driver, PeekPokeTester}

import consts.CsrOp._
import consts.CSR._
import consts.ExceptCause._

class CsrFileUnitTester(c: CsrFile) extends PeekPokeTester(c) {
  def testRead(op: BigInt, addr: BigInt, valid: Boolean, data: BigInt) = {
    poke(c.io.read.op, op)
    poke(c.io.read.addr, addr)
    expect(c.io.read.valid, valid)
    if (valid) expect(c.io.read.data, data)
  }

  def pokeWrite(op: BigInt, addr: BigInt, data: Int) = {
    poke(c.io.write.op, op)
    poke(c.io.write.addr, addr)
    poke(c.io.write.data, data)
    poke(c.io.write.retired, true)
  }

  def pokeWrite() = {
    poke(c.io.write.op, CSR_NOP)
    poke(c.io.write.addr, 0)
    poke(c.io.write.data, 0)
    poke(c.io.write.retired, false)
  }

  def pokeExcept(sret: Boolean, mret: Boolean, pc: Int) = {
    poke(c.io.except.hasTrap, true)
    poke(c.io.except.isSret, sret)
    poke(c.io.except.isMret, mret)
    poke(c.io.except.excCause, 0)
    poke(c.io.except.excPc, pc)
    poke(c.io.except.excValue, 0)
  }

  def pokeExcept(cause: BigInt, pc: Int, value: Int) = {
    poke(c.io.except.hasTrap, true)
    poke(c.io.except.isSret, false)
    poke(c.io.except.isMret, false)
    poke(c.io.except.excCause, cause)
    poke(c.io.except.excPc, pc)
    poke(c.io.except.excValue, value)
  }

  def pokeExcept() = {
    poke(c.io.except.hasTrap, false)
    poke(c.io.except.isSret, false)
    poke(c.io.except.isMret, false)
    poke(c.io.except.excCause, 0)
    poke(c.io.except.excPc, 0)
    poke(c.io.except.excValue, 0)
  }

  def pokeInt(timer: Boolean, soft: Boolean, extern: Boolean) = {
    poke(c.io.irq.timer, timer)
    poke(c.io.irq.soft, soft)
    poke(c.io.irq.extern, extern)
  }

  def pokeInt() = {
    poke(c.io.irq.timer, false)
    poke(c.io.irq.soft, false)
    poke(c.io.irq.extern, false)
  }

  def expectMode(mode: BigInt) = {
    expect(c.io.mode, mode)
  }

  // must in M-mode after reset
  expectMode(CSR_MODE_M)

  // test CSR read
  testRead(CSR_R, CSR_CYCLE, true, 0)
  testRead(CSR_W, CSR_CYCLE, false, 0)
  testRead(CSR_RW, CSR_MVENDERID, false, 0)

  // CSR write
  pokeWrite(CSR_W, CSR_MEPC, 0x00000103)
  step(1)
  testRead(CSR_R, CSR_MEPC, true, 0x00000100)
  pokeWrite(CSR_RW, CSR_MEDELEG, 0xffff)
  step(1)
  pokeWrite(CSR_W, CSR_MIE, 0xffff)
  step(1)
  testRead(CSR_R, CSR_MEDELEG, true, 0x0000b35d)

  // return to S-mode
  pokeWrite(CSR_RS, CSR_MSTATUS, 0x00000880)
  step(1)
  pokeWrite()
  pokeExcept(false, true, 0x00000200)
  step(1)
  pokeExcept()
  expectMode(CSR_MODE_S)
  testRead(CSR_R, CSR_MSTATUS, false, 0)
  pokeWrite(CSR_W, CSR_STVEC, 0x00103301)
  step(1)
  pokeWrite()
  testRead(CSR_R, CSR_STVEC, true, 0x00103301)

  // test exception
  pokeExcept(EXC_S_ECALL, 0x00000100, 0)
  expect(c.io.trapVec, 0x00103300)
  step(1)
  pokeExcept()
  expectMode(CSR_MODE_S)
  testRead(CSR_R, CSR_SEPC, true, 0x00000100)
  testRead(CSR_R, CSR_SCAUSE, true, EXC_S_ECALL)
  testRead(CSR_R, CSR_SSTATUS, true, 0x00000100)

  // test interrupt
  pokeInt(true, false, false)
  step(1)
  expect(c.io.hasInt, true)
  pokeExcept(0, 0x00000100, 0)
  expect(c.io.trapVec, 0x00000000)
  step(1)
  pokeExcept()
  expectMode(CSR_MODE_M)
}

object CsrFileTest extends App {
  if (!Driver.execute(args, () => new CsrFile) {
    (c) => new CsrFileUnitTester(c)
  }) sys.exit(1)
}
