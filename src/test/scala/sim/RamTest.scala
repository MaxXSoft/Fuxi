package sim

import chisel3.iotesters.{Driver, PeekPokeTester}

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

object RamTest extends App {
  if (!Driver.execute(args, () => new RAM) {
    (c) => new RamUnitTester(c)
  }) sys.exit(1)
}
