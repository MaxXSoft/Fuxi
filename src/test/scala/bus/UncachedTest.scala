package bus

import chisel3.iotesters.{Driver, PeekPokeTester}

class UncachedUnitTester(c: Uncached) extends PeekPokeTester(c) {
  def initialize() = {
    poke(c.io.sram.en, false)
    poke(c.io.sram.wen, 0)
    poke(c.io.sram.addr, 0)
    poke(c.io.sram.wdata, 0)
    poke(c.io.axi.readAddr.ready, false)
    poke(c.io.axi.readData.valid, false)
    poke(c.io.axi.readData.bits.data, 0)
    poke(c.io.axi.readData.bits.id, 0)
    poke(c.io.axi.readData.bits.last, false)
    poke(c.io.axi.readData.bits.resp, 0)
    poke(c.io.axi.writeAddr.ready, false)
    poke(c.io.axi.writeData.ready, false)
    poke(c.io.axi.writeResp.valid, false)
    poke(c.io.axi.writeResp.bits.id, 0)
    poke(c.io.axi.writeResp.bits.resp, 0)
  }

  def testWrite(response: Int, fault: Boolean) = {
    poke(c.io.sram.en, true)
    poke(c.io.sram.wen, 0xf)
    poke(c.io.sram.addr, 0x10001000)
    poke(c.io.sram.wdata, 0x12345678)
    step(1)

    expect(c.io.axi.writeAddr.valid, true)
    poke(c.io.axi.writeAddr.ready, true)
    step(1)
    poke(c.io.axi.writeAddr.ready, false)

    expect(c.io.axi.writeData.valid, true)
    expect(c.io.axi.writeData.bits.last, true)
    poke(c.io.axi.writeData.ready, true)
    step(1)
    poke(c.io.axi.writeData.ready, false)

    // W has completed, but the SRAM request must remain pending until B.
    for (_ <- 0 until 4) {
      expect(c.io.axi.writeResp.ready, true)
      expect(c.io.sram.valid, false)
      step(1)
    }

    poke(c.io.axi.writeResp.bits.resp, response)
    poke(c.io.axi.writeResp.valid, true)
    step(1)
    poke(c.io.axi.writeResp.valid, false)
    expect(c.io.sram.valid, true)
    expect(c.io.sram.accessFault, fault)
    poke(c.io.sram.en, false)
    step(1)
  }

  def testRead(response: Int, fault: Boolean) = {
    val data = BigInt("89abcdef", 16)
    poke(c.io.sram.en, true)
    poke(c.io.sram.wen, 0)
    poke(c.io.sram.addr, 0x10000000)
    step(1)

    expect(c.io.axi.readAddr.valid, true)
    poke(c.io.axi.readAddr.ready, true)
    step(1)
    poke(c.io.axi.readAddr.ready, false)

    expect(c.io.axi.readData.ready, true)
    expect(c.io.sram.valid, false)
    poke(c.io.axi.readData.bits.data, data)
    poke(c.io.axi.readData.bits.last, true)
    poke(c.io.axi.readData.bits.resp, response)
    poke(c.io.axi.readData.valid, true)
    step(1)
    poke(c.io.axi.readData.valid, false)

    expect(c.io.sram.valid, true)
    expect(c.io.sram.accessFault, fault)
    expect(c.io.sram.rdata, data)
    poke(c.io.sram.en, false)
    step(1)
  }

  initialize()
  step(1)
  testWrite(0, false)
  testWrite(2, true)
  testRead(0, false)
  testRead(3, true)
}

object UncachedTest extends App {
  if (!Driver.execute(args, () => new Uncached) {
    (c) => new UncachedUnitTester(c)
  }) sys.exit(1)
}
