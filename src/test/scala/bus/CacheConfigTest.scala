package bus

import org.scalatest.funsuite.AnyFunSuite

import circt.stage.ChiselStage
import configs._
import org.chipsalliance.cde.config.Parameters
import utils.{AxiTestSupport, PeekPokeTester, TestDriver}

private class ConfiguredRoutingTester(c: CoreBus, routes: Seq[(BigInt, Boolean)] = Seq(
  BigInt("10000000", 16) -> false,
  BigInt("effffffc", 16) -> false,
  BigInt("f0000000", 16) -> true,
  BigInt("fffffffc", 16) -> true,
)) extends PeekPokeTester(c)
    with AxiTestSupport {
  for (port <- Seq(c.io.rom, c.io.ram)) {
    poke(port.en, false)
    poke(port.wen, 0)
    poke(port.addr, 0)
    poke(port.wdata, 0)
    poke(port.size, 2)
  }
  poke(c.io.tlb.en, false)
  poke(c.io.tlb.dataEn, false)
  poke(c.io.tlb.mxr, false)
  poke(c.io.tlb.dataWrite, false)
  poke(c.io.tlb.flushInst, false)
  poke(c.io.tlb.flushData, false)
  poke(c.io.tlb.basePpn, 0)
  poke(c.io.tlb.sum, false)
  poke(c.io.tlb.smode, false)
  poke(c.io.tlb.dataSmode, false)
  poke(c.io.cache.flushInst, false)
  poke(c.io.cache.flushData, false)
  for (port <- Seq(c.io.inst, c.io.data, c.io.uncached)) idleAxi(port)
  def memory(address: BigInt): BigInt = address ^ BigInt("12345678", 16)

  for ((address, uncached) <- routes) {
    val selected = if (uncached) c.io.uncached else c.io.data
    val other = if (uncached) c.io.data else c.io.uncached
    val responder = new AxiReadResponder(selected, memory)
    poke(c.io.ram.addr, address)
    poke(c.io.ram.en, true)
    runUntil(c.DCACHE_LINE_BYTES / 4 + 40, s"Configured route for 0x${address.toString(16)}") {
      responder.beforeStep()
      expect(other.readAddr.valid, false)
      expect(c.io.inst.readAddr.valid, false)
      expect(c.io.ram.fault, false)
      expect(c.io.ram.accessFault, false)
      val done = peek(c.io.ram.valid) != 0
      step(1)
      responder.afterStep()
      done
    }
    expect(c.io.ram.rdata, memory(address))
    poke(c.io.ram.en, false)
    assert(responder.requests.size == 1)
    val request = responder.requests.head
    assert(request.beats == (if (uncached) 1 else c.DCACHE_LINE_BYTES / 4))
    if (!uncached) {
      assert(request.address + request.beats * 4 <= c.DATA_UNCACHED_START ||
        request.address >= c.DATA_UNCACHED_END, "Cached refill crossed into the uncached range")
    }
    idleAxi(selected)
    step(1)
  }
}

class CacheConfigTest extends AnyFunSuite {
  test("reject unaligned uncached boundaries when constructing CoreBus directly") {
    val base = BigInt("10000000", 16)
    for ((start, end) <- Seq((base + 4, base + 64), (base, base + 60),
                             (base + 4, base + 8))) {
      val p = new WithDataUncachedRegion(start, end) ++ new DefaultConfig
      // Deliberately bypass ConfigValidation and the CLI.
      val error = intercept[IllegalArgumentException] {
        ChiselStage.emitCHIRRTL(new CoreBus()(p))
      }
      assert(error.getMessage.contains("uncached range"))
    }
  }

  test("keep cached refills outside aligned uncached boundaries") {
    val base = BigInt("10000000", 16)
    for (lineBytes <- Seq(8, 64)) {
      val start = base + lineBytes
      val end = start + lineBytes
      val p = new WithICacheLines(2) ++ new WithICacheLineBytes(1024) ++
        new WithDCacheLines(2) ++ new WithDCacheLineBytes(lineBytes) ++
        new WithDataUncachedRegion(start, end) ++ new DefaultConfig
      TestDriver.run(new CoreBus()(p)) { c =>
        new ConfiguredRoutingTester(c, Seq(start - 4 -> false, start -> true,
          end - 4 -> true, end -> false))
      }
    }
  }

  test("support cache geometries and configured memory routing") {
    // Include both AXI burst-length boundaries and asymmetric I/D geometries.
    for ((iLines, iBytes, dLines, dBytes) <- Seq((2, 8, 4, 32), (4, 128, 2, 1024))) {
      implicit val p: Parameters = new WithICacheLines(iLines) ++
        new WithICacheLineBytes(iBytes) ++
        new WithDCacheLines(dLines) ++
        new WithDCacheLineBytes(dBytes) ++
        new DefaultConfig
      TestDriver.run(new InstCache) {
        c => new InstCachePerformanceTester(c)
      }
      TestDriver.run(new DataCache) {
        c => new DataCachePerformanceTester(c)
      }
    }
    val platform = new WithITlbEntries(2) ++
      new WithDTlbEntries(4) ++
      new WithICacheLines(2) ++
      new WithDCacheLines(4) ++
      new WithDataUncachedRegion(BigInt("f0000000", 16), BigInt(1) << 32) ++
      new DefaultConfig
    TestDriver.run(new CoreBus()(platform)) {
      c => new CoreBusPerformanceTester(c)
    }
    TestDriver.run(new CoreBus()(platform)) {
      // The old MMIO range is now cached; the final RV32 word is uncached
      // with an exclusive upper bound represented by 33 bits.
      c => new ConfiguredRoutingTester(c)
    }
  }
}
