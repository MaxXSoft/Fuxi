package core

import chisel3.iotesters.{Driver, PeekPokeTester}

class FetchUnitTester(c: Fetch) extends PeekPokeTester(c) {
  def testNormal(pc: BigInt) = {
    poke(c.io.flush, false)
    poke(c.io.stall, false)
    poke(c.io.flushPc, 0)
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
    poke(c.io.branch.taken, false)
    poke(c.io.branch.index, 0)
    poke(c.io.branch.pc, 0)
    poke(c.io.branch.target, 0)
    step(1)
    expect(c.io.fetch.pc, pc)
  }

  def testStall(pc: BigInt) = {
    poke(c.io.flush, false)
    poke(c.io.stall, true)
    poke(c.io.flushPc, 0)
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
    poke(c.io.branch.taken, false)
    poke(c.io.branch.index, 0)
    poke(c.io.branch.pc, 0)
    poke(c.io.branch.target, 0)
    step(1)
    expect(c.io.fetch.pc, pc)
  }

  def testFlush(pc: BigInt) = {
    poke(c.io.flush, true)
    poke(c.io.stall, false)
    poke(c.io.flushPc, pc)
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
    poke(c.io.branch.taken, false)
    poke(c.io.branch.index, 0)
    poke(c.io.branch.pc, 0)
    poke(c.io.branch.target, 0)
    step(1)
    expect(c.io.fetch.pc, pc)
  }

  testNormal(0x00000204)
  testNormal(0x00000208)
  testNormal(0x0000020c)
  testNormal(0x00000210)
  testStall (0x00000210)
  testNormal(0x00000214)
  testFlush (0x00000200)
  testNormal(0x00000204)
  testNormal(0x00000208)
  testNormal(0x0000020c)
}

object FetchTest extends App {
  if (!Driver.execute(args, () => new Fetch) {
    (c) => new FetchUnitTester(c)
  }) sys.exit(1)
}
