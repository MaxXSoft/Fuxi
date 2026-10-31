package configs

import org.chipsalliance.cde.config.Config

private[configs] class WithCoreParams(update: CoreParams => CoreParams)
    extends Config((site, here, up) => {
  case CoreParamsKey => update(up(CoreParamsKey))
})

private[configs] class WithPlatformParams(update: PlatformParams => PlatformParams)
    extends Config((site, here, up) => {
  case PlatformParamsKey => update(up(PlatformParamsKey))
})

class WithFetchQueueDepth(n: Int)
  extends WithCoreParams(p => p.copy(frontend = p.frontend.copy(fetchQueueDepth = n)))

class WithFetchBufferEntries(n: Int)
  extends WithCoreParams(p => p.copy(frontend = p.frontend.copy(fetchBufferEntries = n)))

class WithGhrBits(n: Int)
  extends WithCoreParams(p => p.copy(bpu = p.bpu.copy(ghrBits = n)))

class WithBtbEntries(n: Int)
  extends WithCoreParams(p => p.copy(bpu = p.bpu.copy(btbEntries = n)))

class WithRasEntries(n: Int)
  extends WithCoreParams(p => p.copy(bpu = p.bpu.copy(rasEntries = n)))

class WithICacheLines(n: Int)
  extends WithCoreParams(p => p.copy(icache = p.icache.copy(lines = n)))

class WithDCacheLines(n: Int)
  extends WithCoreParams(p => p.copy(dcache = p.dcache.copy(lines = n)))

class WithICacheLineBytes(n: Int)
  extends WithCoreParams(p => p.copy(icache = p.icache.copy(lineBytes = n)))

class WithDCacheLineBytes(n: Int)
  extends WithCoreParams(p => p.copy(dcache = p.dcache.copy(lineBytes = n)))

class WithCacheLineBytes(n: Int) extends Config(
  new WithICacheLineBytes(n) ++ new WithDCacheLineBytes(n)
)

class WithITlbEntries(n: Int)
  extends WithCoreParams(p => p.copy(mmu = p.mmu.copy(itlbEntries = n)))

class WithDTlbEntries(n: Int)
  extends WithCoreParams(p => p.copy(mmu = p.mmu.copy(dtlbEntries = n)))

class WithMulLatency(n: Int) extends WithCoreParams(_.copy(mulLatency = n))

class WithHpmCounters(n: Int) extends WithCoreParams(_.copy(hpmCounters = n))

class WithResetVector(address: BigInt)
  extends WithPlatformParams(_.copy(resetVector = address))

class WithDataUncachedRegion(start: BigInt, end: BigInt)
    extends WithPlatformParams(_.copy(dataUncached = AddressRange(start, end)))

class WithHartId(id: BigInt) extends WithPlatformParams(_.copy(hartId = id))
