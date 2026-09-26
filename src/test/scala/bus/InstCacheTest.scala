package bus

import utils.{PeekPokeTester, TestDriver}

class InstCacheFlushTester(c: InstCache) extends PeekPokeTester(c) {
  val oldLine = BigInt("80000000", 16)
  val pendingLine = oldLine + 64
  poke(c.io.sram.en, false)
  poke(c.io.sram.wen, 0)
  poke(c.io.sram.addr, oldLine)
  poke(c.io.sram.wdata, 0)
  poke(c.io.flush, false)
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

  def begin(address: BigInt): Unit = {
    poke(c.io.sram.addr, address)
    poke(c.io.sram.en, true)
    expect(c.io.sram.valid, false)
    step(1)
    expect(c.io.axi.readAddr.valid, true)
    expect(c.io.axi.readAddr.bits.addr, address)
  }
  def acceptAddress(): Unit = {
    poke(c.io.axi.readAddr.ready, true)
    step(1)
    poke(c.io.axi.readAddr.ready, false)
  }
  def beat(index: Int, data: BigInt): Unit = {
    poke(c.io.axi.readData.valid, true)
    poke(c.io.axi.readData.bits.data, data + index)
    poke(c.io.axi.readData.bits.last, index == 15)
    expect(c.io.axi.readData.ready, true)
    step(1)
    poke(c.io.axi.readData.valid, false)
  }
  def pulseFlush(): Unit = {
    poke(c.io.flush, true)
    step(1)
    poke(c.io.flush, false)
  }

  for (phase <- Seq("address", "data", "last", "update")) {
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
    begin(oldLine)
    acceptAddress()
    for (i <- 0 until 16) beat(i, 0x11110000)
    step(1)
    expect(c.io.sram.valid, true)
    step(1)
    expect(c.io.sram.rdata, 0x11110000)

    begin(pendingLine)
    // A fence may finish while the instruction address channel is stalled.
    if (phase == "address") {
      pulseFlush()
      step(3)
      expect(c.io.axi.readAddr.valid, true)
      expect(c.io.axi.readAddr.bits.addr, pendingLine)
    }
    acceptAddress()
    for (i <- 0 until 16) {
      poke(c.io.flush, (phase == "data" && i == 3) ||
        (phase == "last" && i == 15))
      beat(i, 0x22220000)
      poke(c.io.flush, false)
      if (i == 7) step(3) // response-channel gaps must still drain correctly
    }
    // The pipeline has redirected; do not create a second demand request yet.
    poke(c.io.sram.en, false)
    if (phase == "update") pulseFlush() else step(1)
    expect(c.io.sram.valid, false) // discarded refill must not become a hit
    poke(c.io.sram.addr, oldLine)
    expect(c.io.sram.valid, false) // unrelated old lines must also be invalid

    // A fresh post-fence refill must work and expose new instruction bytes.
    begin(oldLine)
    acceptAddress()
    for (i <- 0 until 16) beat(i, 0x33330000)
    step(1)
    expect(c.io.sram.valid, true)
    step(1)
    expect(c.io.sram.rdata, 0x33330000)
    poke(c.io.sram.en, false)
  }
}

class InstCacheFaultOwnershipTester(c: InstCache) extends PeekPokeTester(c) {
  val oldWord = BigInt("80000004", 16)
  val newWord = BigInt("80000048", 16)
  poke(c.io.sram.en, false)
  poke(c.io.sram.wen, 0)
  poke(c.io.sram.addr, oldWord)
  poke(c.io.sram.wdata, 0)
  poke(c.io.flush, false)
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

  def address(expected: BigInt): Unit = {
    expect(c.io.axi.readAddr.valid, true)
    expect(c.io.axi.readAddr.bits.addr, expected & ~BigInt(63))
    poke(c.io.axi.readAddr.ready, true)
    step(1)
    poke(c.io.axi.readAddr.ready, false)
  }
  def refill(fault: Boolean): Unit = {
    for (beat <- 0 until 16) {
      poke(c.io.axi.readData.valid, true)
      poke(c.io.axi.readData.bits.data, 0x12340000 + beat)
      poke(c.io.axi.readData.bits.resp, if (fault && beat == 3) 2 else 0)
      poke(c.io.axi.readData.bits.last, beat == 15)
      expect(c.io.axi.readData.ready, true)
      step(1)
      poke(c.io.axi.readData.valid, false)
    }
  }

  for (changedOwner <- Seq(true, false)) {
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
    poke(c.io.sram.en, true)
    poke(c.io.sram.addr, oldWord)
    step(1)
    address(oldWord)
    // A translation/context change can complete the old virtual lookup before
    // its physical refill drains, leaving another lookup on the shared port.
    if (changedOwner) poke(c.io.sram.addr, newWord)
    refill(fault = true)
    expect(c.io.sram.valid, !changedOwner)
    expect(c.io.sram.accessFault, !changedOwner)
    expect(c.io.sram.fault, false)
    step(1)
    expect(c.io.sram.valid, false)
    expect(c.io.sram.accessFault, false)
    if (changedOwner) {
      step(1)
      address(newWord)
      refill(fault = false)
      step(1)
      expect(c.io.sram.valid, true)
      expect(c.io.sram.accessFault, false)
      step(1)
      expect(c.io.sram.rdata, 0x12340002)
    }
    poke(c.io.sram.en, false)
  }
}

object InstCacheTest extends App {
  if (!TestDriver.execute(args, () => new InstCache) {
    c => new InstCacheFlushTester(c)
  }) sys.exit(1)
  if (!TestDriver.execute(args, () => new InstCache) {
    c => new InstCacheFaultOwnershipTester(c)
  }) sys.exit(1)
}
