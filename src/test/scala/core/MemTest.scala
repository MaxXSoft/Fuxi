package core

import chisel3._
import chisel3.iotesters.{Driver, PeekPokeTester}

import io._
import consts.Parameters._
import consts.LsuOp._
import consts.ExceptType._
import consts.ExceptCause._
import consts.CSR._
import utils.MidStage

class MemUnitTester(c: Mem) extends PeekPokeTester(c) {
  val wdata = BigInt("abcdef00", 16)
  val rdata = BigInt("8badf00d", 16)
  val mask  = BigInt("ffffffff", 16)
  val inst  = BigInt("33445566", 16)
  val maxLatency = 32

  def simulateLatency(checkStall: Boolean = true) = {
    val latency = rnd.nextInt(maxLatency)
    poke(c.io.csrBusy, false)
    for (i <- 0 until latency) {
      poke(c.io.ram.valid, false)
      poke(c.io.ram.rdata, 0)
      step(1)
      expect(c.io.stallReq, true)
    }
    poke(c.io.ram.valid, true)
    poke(c.io.ram.rdata, rdata)
    step(1)
    if (checkStall) expect(c.io.stallReq, false)
  }

  def pokeLsu(op: BigInt, addr: Int) = {
    poke(c.io.alu.reg.data, addr)
    poke(c.io.alu.lsuOp, op)
    poke(c.io.alu.lsuData, wdata)
    poke(c.io.flush, false)
    poke(c.io.ram.fault, false)
  }

  def pokeExc(excType: BigInt, pc: Int,
              hasInt: Boolean, userMode: Boolean) = {
    poke(c.io.alu.excType, excType)
    poke(c.io.alu.valid, true)
    poke(c.io.alu.inst, inst)
    poke(c.io.alu.currentPc, pc)
    poke(c.io.csrHasInt, hasInt)
    poke(c.io.csrMode, if (userMode) CSR_MODE_U else CSR_MODE_M)
  }

  def pokeEm(addr: Int, valid: Boolean) = {
    expect(c.io.excMon.addr, addr)
    poke(c.io.excMon.valid, valid)
  }

  def expectLsu(wen: Int, data: BigInt) = {
    expect(c.io.ram.en, true)
    expect(c.io.ram.wen, wen)
    expect(c.io.ram.wdata, data & mask)
  }

  def expectLsu(wen: Int) = {
    if (wen != 0) {
      expect(c.io.ram.en, true)
      expect(c.io.ram.wen, wen)
      expect(c.io.ram.wdata, wdata)
    }
  }

  def expectLsu() = {
    expect(c.io.ram.en, true)
  }

  def expectExc(sret: Boolean, mret: Boolean, pc: Int) = {
    expect(c.io.except.hasTrap, 1)
    expect(c.io.except.isSret, if (sret) 1 else 0)
    expect(c.io.except.isMret, if (mret) 1 else 0)
    expect(c.io.except.excPc, pc)
  }

  def expectExc(cause: BigInt, pc: Int, excVal: BigInt) = {
    expect(c.io.except.hasTrap, true)
    expect(c.io.except.isSret, false)
    expect(c.io.except.isMret, false)
    expect(c.io.except.excCause, cause)
    expect(c.io.except.excPc, pc)
    expect(c.io.except.excValue, excVal)
  }

  def expectExc(cause: BigInt, pc: Int) = {
    expect(c.io.except.hasTrap, true)
    expect(c.io.except.isSret, false)
    expect(c.io.except.isMret, false)
    expect(c.io.except.excCause, cause)
    expect(c.io.except.excPc, pc)
  }

  def expectExc() = {
    expect(c.io.except.hasTrap, false)
  }

  def expectEm(addr: Int, isSet: Boolean) = {
    expect(c.io.mem.excMon.addr, addr)
    expect(c.io.mem.excMon.set, isSet)
    expect(c.io.mem.excMon.clear, !isSet)
  }

  def expectEm(wen: Boolean) = {
    expect(c.io.mem.excMon.set, false)
    expect(c.io.mem.excMon.clear, wen)
  }

  // SB
  pokeLsu(LSU_SB, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(1)
  expectEm(true)
  expectExc()
  pokeLsu(LSU_SB, 0x12345679)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(2, wdata << 8)
  expectEm(true)
  expectExc()
  pokeLsu(LSU_SB, 0x1234567a)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(4, wdata << 16)
  expectEm(true)
  expectExc()
  pokeLsu(LSU_SB, 0x1234567b)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(8, wdata << 24)
  expectEm(true)
  expectExc()

  // SH
  pokeLsu(LSU_SH, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(3)
  expectEm(true)
  expectExc()
  pokeLsu(LSU_SH, 0x12345679)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(0)
  expectEm(true)
  expectExc(EXC_STAMO_ADDR, 0x00000200, 0x12345679)
  pokeLsu(LSU_SH, 0x1234567a)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(12, wdata << 16)
  expectEm(true)
  expectExc()
  pokeLsu(LSU_SH, 0x1234567b)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(0)
  expectEm(true)
  expectExc(EXC_STAMO_ADDR, 0x00000200, 0x1234567b)

  // SW
  pokeLsu(LSU_SW, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(15)
  expectEm(true)
  expectExc()
  pokeLsu(LSU_SW, 0x12345679)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(0)
  expectEm(true)
  expectExc(EXC_STAMO_ADDR, 0x00000200, 0x12345679)
  pokeLsu(LSU_SW, 0x1234567a)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(0)
  expectEm(true)
  expectExc(EXC_STAMO_ADDR, 0x00000200, 0x1234567a)
  pokeLsu(LSU_SW, 0x1234567b)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency()
  expectLsu(0)
  expectEm(true)
  expectExc(EXC_STAMO_ADDR, 0x00000200, 0x1234567b)

  // LW
  pokeLsu(LSU_LW, 0x12345678)
  pokeExc(EXC_LOAD, 0x00000200, false, false)
  simulateLatency()
  expectLsu()
  expectEm(false)
  expectExc()
  pokeLsu(LSU_LW, 0x12345679)
  pokeExc(EXC_LOAD, 0x00000200, false, false)
  simulateLatency()
  expectEm(false)
  expectExc(EXC_LOAD_ADDR, 0x00000200, 0x12345679)

  // LR/SC
  pokeLsu(LSU_LR, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  pokeEm(0x12345678, false)
  simulateLatency()
  expectLsu()
  expectExc()
  expectEm(0x12345678, true)
  pokeLsu(LSU_SC, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  pokeEm(0x12345678, true)
  simulateLatency()
  expectLsu(15)
  expectEm(0x12345678, false)
  expect(c.io.mem.reg.data, 0)
  pokeLsu(LSU_SC, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  pokeEm(0x12345678, false)
  simulateLatency()
  expectLsu(0)
  expectEm(0x12345678, false)
  expect(c.io.mem.reg.data, 1)

  // AMOADD
  pokeLsu(LSU_ADD, 0x12345678)
  pokeExc(EXC_STAMO, 0x00000200, false, false)
  simulateLatency(false)
  expect(c.io.stallReq, true)
  expectLsu(15, rdata + wdata)
  simulateLatency()
  expect(c.io.mem.reg.data, rdata)
  expectLsu()
  expectExc()
  expectEm(true)

  // FENCE
  pokeLsu(LSU_FENC, 0)
  pokeExc(EXC_NONE, 0x00000200, false, false)
  expect(c.io.stallReq, false)
  expect(c.io.flushIc, false)
  expect(c.io.flushDc, false)
  expect(c.io.flushIt, false)
  expect(c.io.flushDt, false)
  expectExc()
  expectEm(false)

  // FENCE.I
  pokeLsu(LSU_FENI, 0)
  pokeExc(EXC_NONE, 0x00000200, false, false)
  expect(c.io.stallReq, false)
  expect(c.io.flushIc, true)
  expect(c.io.flushDc, true)
  expect(c.io.flushIt, false)
  expect(c.io.flushDt, false)
  expectExc()
  expectEm(false)

  // SFENCE.VMA
  pokeLsu(LSU_FENV, 0)
  pokeExc(EXC_SPRIV, 0x00000200, false, false)
  expect(c.io.stallReq, false)
  expect(c.io.flushIc, true)
  expect(c.io.flushDc, true)
  expect(c.io.flushIt, true)
  expect(c.io.flushDt, true)
  expectExc()
  expectEm(false)
  pokeLsu(LSU_FENV, 0)
  pokeExc(EXC_SPRIV, 0x00000200, false, true)
  expect(c.io.stallReq, false)
  expect(c.io.flushIc, false)
  expect(c.io.flushDc, false)
  expect(c.io.flushIt, false)
  expect(c.io.flushDt, false)
  expectExc(EXC_ILL_INST, 0x00000200, inst)
  expectEm(false)

  // other exceptions
  pokeLsu(LSU_NOP, 0)
  pokeExc(EXC_ECALL, 0x00000200, false, false)
  expectExc(EXC_M_ECALL, 0x00000200)
  pokeExc(EXC_ECALL, 0x00000200, false, true)
  expectExc(EXC_U_ECALL, 0x00000200)
  pokeExc(EXC_EBRK, 0x00000200, false, false)
  expectExc(EXC_BRK_POINT, 0x00000200)
  pokeExc(EXC_SRET, 0x00000200, false, false)
  expectExc(true, false, 0x00000200)
  pokeExc(EXC_SRET, 0x00000200, false, true)
  expectExc(EXC_ILL_INST, 0x00000200, inst)
  pokeExc(EXC_MRET, 0x00000200, false, false)
  expectExc(false, true, 0x00000200)
  pokeExc(EXC_MRET, 0x00000200, false, true)
  expectExc(EXC_ILL_INST, 0x00000200, inst)
  pokeExc(EXC_ILLEG, 0x00000200, false, false)
  expectExc(EXC_ILL_INST, 0x00000200, inst)
  pokeExc(EXC_IPAGE, 0x00000200, false, false)
  expectExc(EXC_INST_PAGE, 0x00000200, 0x00000200)
}

object MemTest extends App {
  if (!Driver.execute(args, () => new Mem) {
    (c) => new MemUnitTester(c)
  }) sys.exit(1)
}
