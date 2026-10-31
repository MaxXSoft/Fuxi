package sim

import org.scalatest.funsuite.AnyFunSuite

import utils.{PeekPokeTester, TestDriver}

class RamUnitTester(c: RAM) extends PeekPokeTester(c) {
  poke(c.io.en, true)
  poke(c.io.wen, 0xf)
  poke(c.io.addr, 0x4)
  poke(c.io.wdata, 0x12345678)
  step(1)

  poke(c.io.en, true)
  poke(c.io.wen, 0x0)
  poke(c.io.addr, 0x4)
  step(1)
  expect(c.io.rdata, 0x12345678)

  poke(c.io.en, true)
  poke(c.io.wen, 0xa)
  poke(c.io.addr, 0x4)
  poke(c.io.wdata, 0x33004400)
  step(1)

  poke(c.io.en, true)
  poke(c.io.wen, 0x0)
  poke(c.io.addr, 0x4)
  step(1)
  expect(c.io.rdata, 0x33344478)
}

class RamTest extends AnyFunSuite {
  test("read and write simulated RAM") {
    TestDriver.run(new RAM) {
      (c) => new RamUnitTester(c)
    }
  }
}
