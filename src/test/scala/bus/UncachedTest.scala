package bus

import org.scalatest.funsuite.AnyFunSuite

import utils.{AxiTestSupport, PeekPokeTester, TestDriver}

class UncachedUnitTester(c: Uncached) extends PeekPokeTester(c) with AxiTestSupport {
  def initialize() = {
    poke(c.io.sram.en, false)
    poke(c.io.sram.wen, 0)
    poke(c.io.sram.addr, 0)
    poke(c.io.sram.size, 2)
    poke(c.io.sram.wdata, 0)
    idleAxi(c.io.axi)
  }

  def testWrite(response: Int, fault: Boolean, size: Int = 2, offset: Int = 0) = {
    val strobe = ((1 << (1 << size)) - 1) << offset
    poke(c.io.sram.en, true)
    poke(c.io.sram.wen, strobe)
    poke(c.io.sram.addr, 0x10001000 + offset)
    poke(c.io.sram.size, size)
    poke(c.io.sram.wdata, 0x12345678)
    step(1)

    expect(c.io.axi.writeAddr.valid, true)
    for (_ <- 0 until 3) {
      expect(c.io.axi.writeAddr.bits.addr, 0x10001000 + offset)
      expect(c.io.axi.writeAddr.bits.size, size)
      expect(c.io.axi.writeAddr.bits.len, 0)
      step(1)
    }
    poke(c.io.axi.writeAddr.ready, true)
    step(1)
    poke(c.io.axi.writeAddr.ready, false)

    expect(c.io.axi.writeData.valid, true)
    expect(c.io.axi.writeData.bits.last, true)
    expect(c.io.axi.writeData.bits.strb, strobe)
    expect(c.io.axi.writeData.bits.data, 0x12345678)
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

  def testRead(response: Int, fault: Boolean, size: Int = 2, offset: Int = 0) = {
    val data = BigInt("89abcdef", 16)
    poke(c.io.sram.en, true)
    poke(c.io.sram.wen, 0)
    poke(c.io.sram.addr, 0x10000000 + offset)
    poke(c.io.sram.size, size)
    step(1)

    expect(c.io.axi.readAddr.valid, true)
    for (_ <- 0 until 3) {
      expect(c.io.axi.readAddr.bits.addr, 0x10000000 + offset)
      expect(c.io.axi.readAddr.bits.size, size)
      expect(c.io.axi.readAddr.bits.len, 0)
      step(1)
    }
    poke(c.io.axi.readAddr.ready, true)
    step(1)
    poke(c.io.axi.readAddr.ready, false)

    expect(c.io.axi.readData.ready, true)
    expect(c.io.sram.valid, false)
    driveAxiRead(c.io.axi, data, last = true, response = response)
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
  // Every naturally aligned byte/halfword/word position in an AXI data beat.
  for ((size, offset) <- Seq(0 -> 0, 0 -> 1, 0 -> 2, 0 -> 3,
                             1 -> 0, 1 -> 2, 2 -> 0)) {
    testWrite(0, false, size, offset)
    testRead(0, false, size, offset)
  }
  testWrite(2, true)
  testRead(3, true)
}

class UncachedTest extends AnyFunSuite {
  test("complete uncached memory transfers") {
    TestDriver.run(new Uncached) {
      (c) => new UncachedUnitTester(c)
    }
  }
}
