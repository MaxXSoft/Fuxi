package core

import chisel3.UInt
import chisel3.iotesters.{Driver, PeekPokeTester}

import consts.LsuOp._
import lsu.LsuDecode._

class WriteBackUnitTester(c: WriteBack) extends PeekPokeTester(c) {
  val rdata = BigInt("a2345678", 16)
  val mask  = BigInt("ffffffff", 16)

  def signExt(data: BigInt, width: Int, sel: Int) = {
    val m = (1 << width) - 1
    val v = (data >> (sel * 8)) & m
    if ((v & (1 << (width - 1))) != 0) {
      (~m | v) & mask
    }
    else {
      v
    }
  }

  def zeroExt(data: BigInt, width: Int, sel: Int) = {
    val m = (1 << width) - 1
    (data >> (sel * 8)) & m
  }

  def pokeLoad(op: UInt, sel: Int) = {
    poke(c.io.mem.reg.en, true)
    poke(c.io.mem.reg.load, true)
    poke(c.io.ramData, rdata)
    poke(c.io.mem.memSel, sel)
    op match {
      case LSU_LB => {
        poke(c.io.mem.memSigned, true)
        poke(c.io.mem.memWidth, LS_DATA_BYTE)
      }
      case LSU_LH => {
        poke(c.io.mem.memSigned, true)
        poke(c.io.mem.memWidth, LS_DATA_HALF)
      }
      case LSU_LW => {
        poke(c.io.mem.memSigned, false)
        poke(c.io.mem.memWidth, LS_DATA_WORD)
      }
      case LSU_LBU => {
        poke(c.io.mem.memSigned, false)
        poke(c.io.mem.memWidth, LS_DATA_BYTE)
      }
      case LSU_LHU => {
        poke(c.io.mem.memSigned, false)
        poke(c.io.mem.memWidth, LS_DATA_HALF)
      }
      case _ =>
    }
  }

  def expectReg(data: BigInt) = {
    expect(c.io.reg.en, true)
    expect(c.io.reg.data, data)
  }

  def expectLoad(op: UInt, sel: Int) {
    op match {
      case LSU_LB => expectReg(signExt(rdata, 8, sel))
      case LSU_LH => expectReg(signExt(rdata, 16, sel))
      case LSU_LW => expectReg(rdata)
      case LSU_LBU => expectReg(zeroExt(rdata, 8, sel))
      case LSU_LHU => expectReg(zeroExt(rdata, 16, sel))
      case _ =>
    }
  }

  // LB
  pokeLoad(LSU_LB, 0)
  expectLoad(LSU_LB, 0)
  pokeLoad(LSU_LB, 1)
  expectLoad(LSU_LB, 1)
  pokeLoad(LSU_LB, 2)
  expectLoad(LSU_LB, 2)
  pokeLoad(LSU_LB, 3)
  expectLoad(LSU_LB, 3)

  // LH
  pokeLoad(LSU_LH, 0)
  expectLoad(LSU_LH, 0)
  pokeLoad(LSU_LH, 2)
  expectLoad(LSU_LH, 2)

  // LW
  pokeLoad(LSU_LW, 0)
  expectLoad(LSU_LW, 0)

  // LBU
  pokeLoad(LSU_LBU, 0)
  expectLoad(LSU_LBU, 0)
  pokeLoad(LSU_LBU, 1)
  expectLoad(LSU_LBU, 1)
  pokeLoad(LSU_LBU, 2)
  expectLoad(LSU_LBU, 2)
  pokeLoad(LSU_LBU, 3)
  expectLoad(LSU_LBU, 3)

  // LHU
  pokeLoad(LSU_LHU, 0)
  expectLoad(LSU_LHU, 0)
  pokeLoad(LSU_LHU, 2)
  expectLoad(LSU_LHU, 2)

  // normal write back
  poke(c.io.mem.reg.en, true)
  poke(c.io.mem.reg.addr, 10)
  poke(c.io.mem.reg.data, 0x12345678)
  poke(c.io.mem.reg.load, false)
  expect(c.io.reg.en, true)
  expect(c.io.reg.addr, 10)
  expect(c.io.reg.data, 0x12345678)
}

object WriteBackTest extends App {
  if (!Driver.execute(args, () => new WriteBack) {
    (c) => new WriteBackUnitTester(c)
  }) sys.exit(1)
}
