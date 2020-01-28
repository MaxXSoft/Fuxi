package lsu

import chisel3.iotesters.{Driver, PeekPokeTester}

class ExclusiveMonitorUnitTester(c: ExclusiveMonitor)
      extends PeekPokeTester(c) {
  val addr1 = 0x12345678
  val addr2 = 0x234abfec

  poke(c.io.set, false)
  poke(c.io.clear, false)
  poke(c.io.addrSet, addr1)
  poke(c.io.addrCheck, addr1)
  step(1)
  expect(c.io.valid, false)

  poke(c.io.set, true)
  poke(c.io.clear, false)
  poke(c.io.addrSet, addr1)
  poke(c.io.addrCheck, addr1)
  step(1)
  expect(c.io.valid, true)

  poke(c.io.set, false)
  poke(c.io.clear, false)
  poke(c.io.addrCheck, addr1)
  step(1)
  expect(c.io.valid, true)

  poke(c.io.set, false)
  poke(c.io.clear, false)
  poke(c.io.addrCheck, addr2)
  step(1)
  expect(c.io.valid, false)

  poke(c.io.set, true)
  poke(c.io.clear, false)
  poke(c.io.addrSet, addr2)
  poke(c.io.addrCheck, addr2)
  step(1)
  expect(c.io.valid, true)

  poke(c.io.set, false)
  poke(c.io.clear, true)
  poke(c.io.addrCheck, addr2)
  step(1)
  expect(c.io.valid, false)
}

object ExclusiveMonitorTest extends App {
  Driver.execute(args, () => new ExclusiveMonitor) {
    (c) => new ExclusiveMonitorUnitTester(c)
  }
}
