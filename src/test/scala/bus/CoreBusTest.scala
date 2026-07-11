package bus

import chisel3.iotesters.{Driver, PeekPokeTester}

class CoreBusUnitTester(c: CoreBus) extends PeekPokeTester(c) {
  poke(c.io.rom.en, false)
  poke(c.io.rom.wen, 0)
  poke(c.io.rom.addr, 0)
  poke(c.io.rom.wdata, 0)

  poke(c.io.ram.en, false)
  poke(c.io.ram.wen, 0)
  poke(c.io.ram.addr, 0x10000000)
  poke(c.io.ram.wdata, 0)

  poke(c.io.tlb.en, false)
  poke(c.io.tlb.flushInst, false)
  poke(c.io.tlb.flushData, false)
  poke(c.io.tlb.basePpn, 0)
  poke(c.io.tlb.sum, false)
  poke(c.io.tlb.smode, false)

  poke(c.io.cache.flushInst, false)
  poke(c.io.cache.flushData, true)

  for (bus <- Seq(c.io.inst, c.io.data, c.io.uncached)) {
    poke(bus.readAddr.ready, false)
    poke(bus.readData.valid, false)
    poke(bus.readData.bits.data, 0)
    poke(bus.readData.bits.id, 0)
    poke(bus.readData.bits.last, false)
    poke(bus.readData.bits.resp, 0)
    poke(bus.writeAddr.ready, false)
    poke(bus.writeData.ready, false)
    poke(bus.writeResp.valid, false)
    poke(bus.writeResp.bits.id, 0)
    poke(bus.writeResp.bits.resp, 0)
  }

  step(1)

  // In Bare mode this address selects the uncached demand path.  D-cache
  // maintenance completion must nevertheless bypass that address routing.
  expect(c.io.ram.valid, false)
  expect(c.io.cache.flushDataDone, true)
  expect(c.io.cache.flushDataAccessFault, false)

  poke(c.io.cache.flushData, false)
  step(1)
  expect(c.io.cache.flushDataDone, false)
}

object CoreBusTest extends App {
  if (!Driver.execute(args, () => new CoreBus) {
    (c) => new CoreBusUnitTester(c)
  }) sys.exit(1)
}
