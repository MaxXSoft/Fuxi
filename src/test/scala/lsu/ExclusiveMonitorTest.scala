package lsu

import utils.{PeekPokeTester, TestDriver}

class ExclusiveMonitorUnitTester(c: ExclusiveMonitor)
      extends PeekPokeTester(c) {
  val addr1 = 0x12345678
  val addr2 = 0x234abfec
  poke(c.io.update.clearAll, false)

  poke(c.io.flush, false)
  poke(c.io.update.set, false)
  poke(c.io.update.clear, false)
  poke(c.io.update.addr, addr1)
  poke(c.io.check.addr, addr1)
  step(1)
  expect(c.io.check.valid, false)

  poke(c.io.flush, false)
  poke(c.io.update.set, true)
  poke(c.io.update.clear, false)
  poke(c.io.update.addr, addr1)
  poke(c.io.check.addr, addr1)
  step(1)
  expect(c.io.check.valid, true)

  poke(c.io.flush, false)
  poke(c.io.update.set, false)
  poke(c.io.update.clear, false)
  poke(c.io.update.addr, addr1)
  poke(c.io.check.addr, addr1)
  step(1)
  expect(c.io.check.valid, true)

  poke(c.io.flush, false)
  poke(c.io.update.set, false)
  poke(c.io.update.clear, false)
  poke(c.io.update.addr, addr2)
  poke(c.io.check.addr, addr2)
  step(1)
  expect(c.io.check.valid, false)

  poke(c.io.flush, false)
  poke(c.io.update.set, true)
  poke(c.io.update.clear, false)
  poke(c.io.update.addr, addr2)
  poke(c.io.check.addr, addr2)
  step(1)
  expect(c.io.check.valid, true)

  poke(c.io.flush, false)
  poke(c.io.update.set, false)
  poke(c.io.update.clear, true)
  poke(c.io.update.addr, addr2)
  poke(c.io.check.addr, addr2)
  step(1)
  expect(c.io.check.valid, false)

  // Ordinary stores invalidate only the reserved word, but any SC consumes
  // the reservation, including a failed SC to a different address.
  def reserve(): Unit = {
    poke(c.io.update.addr, addr1)
    poke(c.io.update.set, true)
    poke(c.io.update.clear, false)
    poke(c.io.update.clearAll, false)
    poke(c.io.check.addr, addr1)
    step(1)
    poke(c.io.update.set, false)
    expect(c.io.check.valid, true)
  }
  reserve()
  poke(c.io.update.addr, addr2)
  poke(c.io.update.clear, true)
  step(1)
  expect(c.io.check.valid, true)
  poke(c.io.update.clear, false)
  poke(c.io.update.clearAll, true)
  step(1)
  expect(c.io.check.valid, false)
  reserve()
  poke(c.io.flush, true)
  step(1)
  expect(c.io.check.valid, false)

}

object ExclusiveMonitorTest extends App {
  if (!TestDriver.execute(args, () => new ExclusiveMonitor) {
    (c) => new ExclusiveMonitorUnitTester(c)
  }) sys.exit(1)
}
