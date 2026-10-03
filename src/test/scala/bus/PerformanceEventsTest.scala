package bus

import consts.Parameters._
import utils.{AxiTestSupport, PeekPokeTester, TestDriver}

class InstCachePerformanceTester(c: InstCache) extends PeekPokeTester(c) with AxiTestSupport {
  poke(c.io.sram.en, false)
  poke(c.io.sram.wen, 0)
  poke(c.io.sram.addr, BigInt("80000000", 16))
  poke(c.io.sram.wdata, 0)
  poke(c.io.flush, false)
  idleAxi(c.io.axi)
  expect(c.io.pmu.access, false)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.busy, false)

  poke(c.io.sram.en, true)
  expect(c.io.pmu.miss, true)
  expect(c.io.pmu.access, false)
  step(1)
  for (_ <- 0 until 5) {
    expect(c.io.pmu.miss, false)
    expect(c.io.pmu.access, false)
    expect(c.io.pmu.busy, true)
    step(1)
  }
  poke(c.io.axi.readAddr.ready, true)
  step(1)
  poke(c.io.axi.readAddr.ready, false)
  for (i <- 0 until ICACHE_LINE_SIZE / 4) {
    expect(c.io.pmu.miss, false)
    expect(c.io.pmu.access, false)
    expect(c.io.pmu.busy, true)
    if (i == 4) step(3)
    driveAxiRead(c.io.axi, 0x100 + i, last = i == ICACHE_LINE_SIZE / 4 - 1)
    step(1)
    poke(c.io.axi.readData.valid, false)
  }
  expect(c.io.pmu.busy, true) // cache tag installation
  expect(c.io.pmu.access, false)
  step(1)
  expect(c.io.pmu.access, true)
  expect(c.io.pmu.miss, false) // refill retry is a completion, not another miss
  expect(c.io.pmu.busy, false)
  step(1)
  poke(c.io.sram.en, false)
  expect(c.io.pmu.access, false)
  poke(c.io.flush, true)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.writeback, false)
  step(1)
  poke(c.io.flush, false)
  poke(c.io.sram.en, true)
  expect(c.io.pmu.miss, true)
}

class DataCachePerformanceTester(c: DataCache) extends PeekPokeTester(c) with AxiTestSupport {
  val base = BigInt("80000000", 16)
  val beats = DCACHE_LINE_SIZE / 4
  poke(c.io.sram.en, false)
  poke(c.io.sram.wen, 0)
  poke(c.io.sram.addr, base)
  poke(c.io.sram.wdata, 0x12345678)
  poke(c.io.flush, false)
  idleAxi(c.io.axi)

  def waitFor(signal: chisel3.Bool): Unit = {
    var count = 0
    while (peek(signal) == 0 && count < 10) {
      expect(c.io.pmu.miss, false)
      step(1)
      count += 1
    }
    expect(signal, true)
  }
  def refill(): Unit = {
    waitFor(c.io.axi.readAddr.valid)
    for (_ <- 0 until 4) {
      expect(c.io.pmu.miss, false)
      expect(c.io.pmu.access, false)
      expect(c.io.pmu.busy, true)
      step(1)
    }
    poke(c.io.axi.readAddr.ready, true)
    step(1)
    poke(c.io.axi.readAddr.ready, false)
    for (i <- 0 until beats) {
      expect(c.io.pmu.miss, false)
      if (i == 3) step(3)
      driveAxiRead(c.io.axi, 0x200 + i, last = i == beats - 1)
      step(1)
      poke(c.io.axi.readData.valid, false)
    }
    expect(c.io.pmu.access, false)
    expect(c.io.pmu.busy, true)
    step(1)
    expect(c.io.pmu.access, true)
    expect(c.io.pmu.miss, false)
    expect(c.io.pmu.busy, false)
  }
  def writeback(): Unit = {
    waitFor(c.io.axi.writeAddr.valid)
    for (_ <- 0 until 4) {
      expect(c.io.pmu.writeback, false)
      expect(c.io.pmu.miss, false)
      expect(c.io.pmu.busy, true)
      step(1)
    }
    poke(c.io.axi.writeAddr.ready, true)
    expect(c.io.pmu.writeback, true)
    step(1)
    poke(c.io.axi.writeAddr.ready, false)
    expect(c.io.pmu.writeback, false)
    waitFor(c.io.axi.writeData.valid)
    poke(c.io.axi.writeData.ready, true)
    for (i <- 0 until beats) {
      expect(c.io.axi.writeData.valid, true)
      expect(c.io.axi.writeData.bits.last, i == beats - 1)
      expect(c.io.pmu.writeback, false)
      expect(c.io.pmu.miss, false)
      step(1)
    }
    poke(c.io.axi.writeData.ready, false)
    poke(c.io.axi.writeResp.valid, true)
    poke(c.io.axi.writeResp.bits.resp, 0)
    expect(c.io.axi.writeResp.ready, true)
    step(1)
    poke(c.io.axi.writeResp.valid, false)
  }

  poke(c.io.sram.en, true)
  expect(c.io.pmu.miss, true)
  step(1)
  refill()
  // A write hit is one SRAM transfer and dirties the line.
  poke(c.io.sram.wen, 15)
  expect(c.io.pmu.access, true)
  expect(c.io.pmu.miss, false)
  step(1)
  poke(c.io.sram.wen, 0)
  poke(c.io.sram.addr, base + DCACHE_SIZE * DCACHE_LINE_SIZE)
  expect(c.io.pmu.miss, true)
  step(1)
  writeback()
  refill() // eviction-to-refill transition must not generate another miss
  poke(c.io.sram.wen, 15)
  step(1)
  poke(c.io.sram.en, false)
  poke(c.io.flush, true)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.access, false)
  step(1)
  writeback() // FENCE writebacks count too, independently of demand traffic
  waitFor(c.io.flushDone)
  expect(c.io.pmu.busy, false)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.access, false)
}

class MmuPerformanceTester(c: MMU) extends PeekPokeTester(c) {
  poke(c.io.en, true)
  poke(c.io.flush, false)
  poke(c.io.basePpn, 0x100)
  poke(c.io.sum, false)
  poke(c.io.mxr, false)
  poke(c.io.smode, true)
  poke(c.io.lookup, false)
  poke(c.io.write, false)
  poke(c.io.vaddr, 0x00400100)
  poke(c.io.data.valid, false)
  poke(c.io.data.fault, false)
  poke(c.io.data.accessFault, false)
  poke(c.io.data.rdata, 0)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.walkCycle, false)
  poke(c.io.lookup, true)
  expect(c.io.pmu.miss, true)
  step(1)
  for (_ <- 0 until 5) {
    expect(c.io.pmu.miss, false)
    expect(c.io.pmu.walkCycle, true)
    step(1)
  }
  // A non-leaf PTE advances the same walk, without a second TLB miss.
  poke(c.io.data.rdata, (0x200 << 10) | 1)
  poke(c.io.data.valid, true)
  step(1)
  poke(c.io.data.valid, false)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.walkCycle, true)
  step(1)
  expect(c.io.data.en, true)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.walkCycle, true)
  // A canceled walk still occupies the walker until its request drains.
  poke(c.io.flush, true)
  step(1)
  poke(c.io.flush, false)
  step(3)
  expect(c.io.pmu.miss, false)
  expect(c.io.pmu.walkCycle, true)
  poke(c.io.data.valid, true)
  step(1)
  poke(c.io.data.valid, false)
  expect(c.io.pmu.walkCycle, false) // sFlush is not a page-walk cycle
  poke(c.io.lookup, false)
  step(1)
  expect(c.io.pmu.miss, false)
  poke(c.io.lookup, true)
  expect(c.io.pmu.miss, true)
  poke(c.io.en, false)
  expect(c.io.pmu.miss, false) // Bare mode never starts a page walk
}

class CoreBusPerformanceTester(c: CoreBus) extends PeekPokeTester(c) with AxiTestSupport {
  // Exercise both actual SRAM demuxes: one page-table transfer and one
  // translated program transfer, each cold, with AXI backpressure.
  for (isInst <- Seq(true, false)) {
    for (port <- Seq(c.io.rom, c.io.ram)) {
      poke(port.en, false)
      poke(port.wen, 0)
      poke(port.addr, 0x00400100)
      poke(port.wdata, 0)
    }
    poke(c.io.tlb.en, true)
    poke(c.io.tlb.dataEn, true)
    poke(c.io.tlb.mxr, false)
    poke(c.io.tlb.dataWrite, false)
    poke(c.io.tlb.flushInst, false)
    poke(c.io.tlb.flushData, false)
    poke(c.io.tlb.basePpn, 0x100)
    poke(c.io.tlb.sum, false)
    poke(c.io.tlb.smode, true)
    poke(c.io.tlb.dataSmode, true)
    poke(c.io.cache.flushInst, false)
    poke(c.io.cache.flushData, false)
    for (port <- Seq(c.io.inst, c.io.data, c.io.uncached)) idleAxi(port)
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
    val port = if (isInst) c.io.rom else c.io.ram
    val axi = if (isInst) c.io.inst else c.io.data
    val access = if (isInst) c.io.pmu.icache.access else c.io.pmu.dcache.access
    val miss = if (isInst) c.io.pmu.icache.miss else c.io.pmu.dcache.miss
    val walkAccess = if (isInst) c.io.pmu.icacheWalkAccess else c.io.pmu.dcacheWalkAccess
    val walkMiss = if (isInst) c.io.pmu.icacheWalkMiss else c.io.pmu.dcacheWalkMiss
    val tlbMiss = if (isInst) c.io.pmu.itlb.miss else c.io.pmu.dtlb.miss
    val signals = Seq(access, miss, walkAccess, walkMiss, tlbMiss)
    val counts = Array.fill(5)(0)
    def tick(): Unit = {
      for (i <- signals.indices) counts(i) += peek(signals(i)).toInt
      step(1)
    }
    poke(port.en, true)
    var cycles = 0
    var requests = 0
    while (peek(port.valid) == 0 && cycles < 100) {
      if (peek(axi.readAddr.valid) != 0) {
        val address = peek(axi.readAddr.bits.addr)
        assert(address == (if (requests == 0) BigInt(0x100000)
                           else BigInt("80000100", 16)))
        requests += 1
        for (_ <- 0 until 3) tick()
        poke(axi.readAddr.ready, true)
        tick()
        poke(axi.readAddr.ready, false)
        for (beat <- 0 until 16) {
          val data = if (requests == 1 && beat == 1)
                       (BigInt(0x80000) << 10) | 0xcf else BigInt(0x13)
          driveAxiRead(axi, data, last = beat == 15)
          tick()
          poke(axi.readData.valid, false)
        }
      } else tick()
      cycles += 1
    }
    expect(port.valid, true)
    tick() // exactly one accepted program transfer
    poke(port.en, false)
    assert(requests == 2)
    assert(counts.toSeq == Seq(2, 2, 1, 1, 1),
      s"${if (isInst) "I" else "D"} cache ownership counts: ${counts.toSeq}")
  }
}

object PerformanceEventsTest extends App {
  if (!TestDriver.execute(args, () => new InstCache) {
    c => new InstCachePerformanceTester(c)
  }) sys.exit(1)
  if (!TestDriver.execute(args, () => new DataCache) {
    c => new DataCachePerformanceTester(c)
  }) sys.exit(1)
  if (!TestDriver.execute(args, () => new MMU(16, false)) {
    c => new MmuPerformanceTester(c)
  }) sys.exit(1)
  if (!TestDriver.execute(args, () => new CoreBus) {
    c => new CoreBusPerformanceTester(c)
  }) sys.exit(1)
}
