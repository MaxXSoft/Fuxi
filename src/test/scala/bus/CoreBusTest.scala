package bus

import utils.{PeekPokeTester, TestDriver}

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
  poke(c.io.tlb.dataWrite, false)
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


class CoreBusFenceTester(c: CoreBus) extends PeekPokeTester(c) {
  val vaddr = BigInt("00400100", 16)
  val oldRoot = BigInt("00100000", 16)
  val newRoot = BigInt("00200000", 16)
  val oldInstruction = BigInt("80000100", 16)
  val newInstruction = BigInt("80400100", 16)
  val oldPte = (BigInt(0x80000) << 10) | 0xcf
  val newPte = (BigInt(0x80400) << 10) | 0xcf
  val instruction = BigInt("00500513", 16) // addi a0, zero, 5

  def initialize(): Unit = {
    poke(c.io.rom.en, false)
    poke(c.io.rom.wen, 0)
    poke(c.io.rom.addr, vaddr)
    poke(c.io.rom.wdata, 0)
    poke(c.io.ram.en, false)
    poke(c.io.ram.wen, 0)
    poke(c.io.ram.addr, 0)
    poke(c.io.ram.wdata, 0)
    poke(c.io.tlb.en, true)
    poke(c.io.tlb.dataWrite, false)
    poke(c.io.tlb.flushInst, false)
    poke(c.io.tlb.flushData, false)
    poke(c.io.tlb.basePpn, oldRoot >> 12)
    poke(c.io.tlb.sum, false)
    poke(c.io.tlb.smode, true)
    poke(c.io.cache.flushInst, false)
    poke(c.io.cache.flushData, false)
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
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
  }

  def checkNoFault(): Unit = {
    expect(c.io.rom.fault, false)
    expect(c.io.rom.accessFault, false)
  }

  def advance(cycles: Int = 1): Unit = {
    for (_ <- 0 until cycles) {
      step(1)
      checkNoFault()
    }
  }

  def setFence(active: Boolean): Unit = {
    poke(c.io.tlb.flushInst, active)
    poke(c.io.tlb.flushData, active)
    poke(c.io.cache.flushInst, active)
    poke(c.io.cache.flushData, active)
    if (active) {
      poke(c.io.tlb.basePpn, newRoot >> 12)
      // This is exactly when Mem can finish SFENCE despite an I-side miss.
      expect(c.io.cache.flushDataDone, true)
      expect(c.io.rom.valid, false)
    }
  }

  def acceptAddress(): Unit = {
    expect(c.io.inst.readAddr.valid, true)
    expect(c.io.inst.readAddr.bits.len, 15)
    expect(c.io.inst.readAddr.bits.size, 2)
    poke(c.io.inst.readAddr.ready, true)
    advance()
    poke(c.io.inst.readAddr.ready, false)
  }

  def beat(index: Int, data: BigInt, response: Int = 0): Unit = {
    expect(c.io.inst.readData.ready, true)
    poke(c.io.inst.readData.valid, true)
    poke(c.io.inst.readData.bits.data, data)
    poke(c.io.inst.readData.bits.resp, response)
    poke(c.io.inst.readData.bits.last, index == 15)
    advance()
    poke(c.io.inst.readData.valid, false)
  }

  for ((phase, responseError) <- Seq(("address", false), ("data", false),
                                     ("last", false), ("data", true))) {
    initialize()
    poke(c.io.rom.en, true)
    var waitCycles = 0
    while (peek(c.io.inst.readAddr.valid) == 0 && waitCycles < 10) {
      advance()
      waitCycles += 1
    }
    expect(c.io.inst.readAddr.valid, true)
    expect(c.io.inst.readAddr.bits.addr, oldRoot)
    if (phase == "address") {
      setFence(true)
      advance()
      setFence(false)
      advance(3)
      expect(c.io.inst.readAddr.valid, true)
      expect(c.io.inst.readAddr.bits.addr, oldRoot)
    }
    acceptAddress()
    for (i <- 0 until 16) {
      val flushNow = (phase == "data" && i == 3) || (phase == "last" && i == 15)
      if (flushNow) setFence(true)
      beat(i, if (i == 1) oldPte else BigInt(0), if (responseError) 2 else 0)
      if (flushNow) setFence(false)
      if (i == 7) advance(3)
    }

    // Exercise the real SRAM demux and cache/MMU completion contracts. The
    // canceled SRAM lookup may retry its PTE refill while draining; that
    // response must still not install the old virtual-to-physical mapping.
    var newRootReads = 0
    var instructionReads = 0
    var totalRequests = 0
    waitCycles = 0
    while (peek(c.io.rom.valid) == 0 && waitCycles < 120) {
      checkNoFault()
      if (peek(c.io.inst.readAddr.valid) != 0) {
        val address = peek(c.io.inst.readAddr.bits.addr)
        assert(address != oldInstruction, "SFENCE restored the old instruction mapping")
        assert(Set(oldRoot, newRoot, newInstruction).contains(address),
          f"Unexpected post-SFENCE AXI address 0x$address%x")
        if (address == newRoot) newRootReads += 1
        if (address == newInstruction) {
          assert(newRootReads > 0, "instruction fetch skipped the replacement page table")
          instructionReads += 1
        }
        totalRequests += 1
        assert(totalRequests <= 4, "canceled page walk failed to make bounded progress")
        acceptAddress()
        for (i <- 0 until 16) {
          val data = if (address == newInstruction) instruction
                     else if (i == 1 && address == newRoot) newPte
                     else if (i == 1) oldPte else BigInt(0)
          beat(i, data)
        }
      } else {
        advance()
      }
      waitCycles += 1
    }
    expect(c.io.rom.valid, true)
    checkNoFault()
    assert(newRootReads == 1 && instructionReads == 1,
      "fetch must complete through the new root and new physical instruction page")
    advance() // SRAM data arrives one cycle after valid.
    expect(c.io.rom.rdata, instruction)
    // Repeating the lookup must hit the replacement translation/cache line.
    advance(3)
    expect(c.io.rom.valid, true)
    expect(c.io.inst.readAddr.valid, false)
    expect(c.io.data.readAddr.valid, false)
    expect(c.io.uncached.readAddr.valid, false)
  }
}

object CoreBusTest extends App {
  if (!TestDriver.execute(args, () => new CoreBus) {
    (c) => new CoreBusUnitTester(c)
  }) sys.exit(1)
  if (!TestDriver.execute(args, () => new CoreBus) {
    (c) => new CoreBusFenceTester(c)
  }) sys.exit(1)
}
