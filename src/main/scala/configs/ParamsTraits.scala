package configs

import org.chipsalliance.cde.config.Parameters

// Supply p as a constructor val so it is available when these vals initialize.
trait HasCoreParams {
  implicit val p: Parameters
  private val coreParams = p(CoreParamsKey)

  final val FETCH_QUEUE_DEPTH = coreParams.frontend.fetchQueueDepth
  final val FETCH_BUFFER_ENTRIES = coreParams.frontend.fetchBufferEntries

  final val GHR_BITS = coreParams.bpu.ghrBits
  final val PHT_ENTRIES = coreParams.bpu.phtEntries
  final val BTB_ENTRIES = coreParams.bpu.btbEntries
  final val BTB_INDEX_BITS = coreParams.bpu.btbIndexBits
  final val BTB_TAG_BITS = coreParams.bpu.btbTagBits
  final val RAS_ENTRIES = coreParams.bpu.rasEntries

  final val ICACHE_LINES = coreParams.icache.lines
  final val ICACHE_LINE_BYTES = coreParams.icache.lineBytes
  final val ICACHE_INDEX_BITS = coreParams.icache.indexBits
  final val ICACHE_OFFSET_BITS = coreParams.icache.offsetBits
  final val ICACHE_CAPACITY_BYTES = coreParams.icache.capacityBytes

  final val DCACHE_LINES = coreParams.dcache.lines
  final val DCACHE_LINE_BYTES = coreParams.dcache.lineBytes
  final val DCACHE_INDEX_BITS = coreParams.dcache.indexBits
  final val DCACHE_OFFSET_BITS = coreParams.dcache.offsetBits
  final val DCACHE_CAPACITY_BYTES = coreParams.dcache.capacityBytes

  final val ITLB_ENTRIES = coreParams.mmu.itlbEntries
  final val DTLB_ENTRIES = coreParams.mmu.dtlbEntries
  final val MUL_LATENCY = coreParams.mulLatency
  final val HPM_COUNTERS = coreParams.hpmCounters
}

// Keep addresses as Scala values; hardware consumers add .U at the use site.
trait HasPlatformParams {
  implicit val p: Parameters
  private val platformParams = p(PlatformParamsKey)

  final val RESET_VECTOR = platformParams.resetVector
  final val HART_ID = platformParams.hartId
  final val DATA_UNCACHED_START = platformParams.dataUncached.start
  final val DATA_UNCACHED_END = platformParams.dataUncached.end
}
