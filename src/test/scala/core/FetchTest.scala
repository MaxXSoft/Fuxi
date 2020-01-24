package core

import chisel3._
import chisel3.iotesters.{Driver, PeekPokeTester}

import io._
import consts.Constants._
import sim.ROM
import utils.ArgParser

class FetchWrapper(initFile: String) extends Module {
  val io = IO(new Bundle {
    val flush   = Input(Bool())
    val stall   = Input(Bool())
    val excPc   = Input(UInt(ADDR_WIDTH.W))
    val branch  = Input(new BranchInfoIO)
    val fetch   = Output(new FetchIO)
  })

  val fetch = Module(new Fetch)
  val rom   = Module(new ROM(initFile))

  fetch.io.flush  := io.flush
  fetch.io.stall  := io.stall
  fetch.io.excPc  := io.excPc
  fetch.io.rom    <> rom.io
  fetch.io.branch <> io.branch
  fetch.io.fetch  <> io.fetch
}

class FetchUnitTester(c: FetchWrapper) extends PeekPokeTester(c) {
  def testNormal(inst: BigInt, pc: BigInt) = {
    poke(c.io.flush, false)
    poke(c.io.stall, false)
    poke(c.io.excPc, 0)
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
    poke(c.io.branch.taken, false)
    poke(c.io.branch.index, 0)
    poke(c.io.branch.pc, 0)
    poke(c.io.branch.target, 0)
    step(1)
    expect(c.io.fetch.inst, inst)
    expect(c.io.fetch.pc, pc)
  }

  def testStall(inst: BigInt, pc: BigInt) = {
    poke(c.io.flush, false)
    poke(c.io.stall, true)
    poke(c.io.excPc, 0)
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
    poke(c.io.branch.taken, false)
    poke(c.io.branch.index, 0)
    poke(c.io.branch.pc, 0)
    poke(c.io.branch.target, 0)
    step(1)
    expect(c.io.fetch.inst, inst)
    expect(c.io.fetch.pc, pc)
  }

  def testFlush(inst: BigInt, pc: BigInt) = {
    poke(c.io.flush, true)
    poke(c.io.stall, false)
    poke(c.io.excPc, pc)
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
    poke(c.io.branch.taken, false)
    poke(c.io.branch.index, 0)
    poke(c.io.branch.pc, 0)
    poke(c.io.branch.target, 0)
    step(1)
    expect(c.io.fetch.inst, inst)
    expect(c.io.fetch.pc, pc)
  }

  testNormal(BigInt("12345678", 16), 0x00000200)
  testNormal(BigInt("abcdef00", 16), 0x00000204)
  testNormal(BigInt("11223344", 16), 0x00000208)
  testNormal(BigInt("aabbdef9", 16), 0x0000020c)
  testStall (BigInt("aabbdef9", 16), 0x0000020c)
  testNormal(BigInt("93948600", 16), 0x00000210)
  testFlush (BigInt("12345678", 16), 0x00000200)
  testNormal(BigInt("abcdef00", 16), 0x00000204)
  testNormal(BigInt("11223344", 16), 0x00000208)
  testNormal(BigInt("aabbdef9", 16), 0x0000020c)
}

object FetchTest extends App {
  var initFile = ""

  val manager = ArgParser(args, (o, v) => {
    o match {
      case Some("--init") => initFile = v; true
      case _ => false
    }
  })

  Driver.execute(() => new FetchWrapper(initFile), manager) {
    (c) => new FetchUnitTester(c)
  }
}
