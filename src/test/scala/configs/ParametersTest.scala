package configs

import org.chipsalliance.cde.config.{Config, Parameters}

object ParametersTest extends App {
  val defaults = new DefaultConfig
  ConfigValidation(defaults)
  val c = defaults(CoreParamsKey)
  val platform = defaults(PlatformParamsKey)
  assert(c.frontend == FrontendParams(4, 4))
  assert(c.bpu == BpuParams(5, 64, 16) && c.bpu.phtEntries == 32)
  assert(c.icache == CacheParams(64, 64) && c.dcache == CacheParams(128, 64))
  assert(c.icache.capacityBytes == 4096 && c.dcache.capacityBytes == 8192)
  assert(c.mmu == MmuParams(16, 16) && c.mulLatency == 1 && c.hpmCounters == 8)
  assert(platform.resetVector == 0x200 && platform.hartId == 0)
  assert(platform.dataUncached == AddressRange(0x10000000, 0x20000000))

  val combined = new WithGhrBits(3)
    .orElse(new WithBtbEntries(16))
    .orElse(new WithGhrBits(4))
    .orElse(new WithCacheLineBytes(32))
    .orElse(new WithResetVector(BigInt("80000000", 16)))
    .orElse(defaults)
  assert(combined(CoreParamsKey).bpu == BpuParams(3, 16, 16))
  assert(combined(CoreParamsKey).icache == CacheParams(64, 32))
  assert(combined(CoreParamsKey).dcache == CacheParams(128, 32))
  assert(combined(PlatformParamsKey).resetVector == BigInt("80000000", 16))
  assert(defaults(CoreParamsKey) == c, "Fragments must not mutate their base")
  assert((new SmallConfig)(CoreParamsKey).hpmCounters == 0)
  assert((new ProfilingConfig)(CoreParamsKey).hpmCounters == 29)

  def rejects(body: => Any): Unit = {
    val failed = try { body; false } catch { case _: IllegalArgumentException => true }
    assert(failed, "Invalid geometry was accepted")
  }
  rejects(FrontendParams(fetchQueueDepth = 0))
  rejects(FrontendParams(fetchBufferEntries = 3))
  rejects(BpuParams(ghrBits = 1))
  rejects(BpuParams(btbEntries = 3))
  rejects(BpuParams(rasEntries = 0))
  rejects(CacheParams(1)); rejects(CacheParams(3))
  rejects(CacheParams(2, 4)); rejects(CacheParams(2, 2048))
  rejects(CacheParams(1 << 30, 8))
  rejects(MmuParams(itlbEntries = 1)); rejects(MmuParams(dtlbEntries = 3))
  rejects(CoreParams(mulLatency = -1)); rejects(CoreParams(hpmCounters = 30))
  rejects(PlatformParams(resetVector = 0x202))
  rejects(PlatformParams(hartId = BigInt(1) << 32))
  rejects(AddressRange(10, 10))
  assert(AddressRange(0, BigInt(1) << 32).end == (BigInt(1) << 32))
  ConfigValidation(new WithRasEntries(1).orElse(new WithFetchQueueDepth(1)).orElse(defaults))

  // Constructor-supplied p must be available to eager trait vals, before the
  // consuming class initializes its own fields (also checked by -Xcheckinit).
  class Consumer(implicit val p: Parameters) extends HasCoreParams with HasPlatformParams {
    val widthsAtConstruction = (GHR_BITS, BTB_TAG_BITS, ICACHE_INDEX_BITS, ICACHE_OFFSET_BITS)
    val resetAtConstruction = RESET_VECTOR
  }
  val customized = new Consumer()(new WithHartId(7)
    .orElse(new WithDataUncachedRegion(BigInt("f0000000", 16), BigInt(1) << 32))
    .orElse(combined))
  assert(customized.widthsAtConstruction == (3, 27, 6, 5))
  assert(customized.PHT_ENTRIES == 8 && customized.ICACHE_CAPACITY_BYTES == 2048)
  assert(customized.resetAtConstruction == BigInt("80000000", 16))
  assert(customized.HART_ID == 7 && customized.DATA_UNCACHED_END == (BigInt(1) << 32))
  val baseline = new Consumer()(defaults)
  assert(baseline.widthsAtConstruction == (5, 25, 6, 6))
  assert(baseline.RESET_VECTOR == 0x200 && baseline.HART_ID == 0)
  assert(customized.GHR_BITS == 3 && customized.HART_ID == 7, "Instances must keep their own configuration")

  // Either trait can be used without providing the other parameter key.
  class CoreConsumer(implicit val p: Parameters) extends HasCoreParams
  class PlatformConsumer(implicit val p: Parameters) extends HasPlatformParams
  val coreOnly = new Config((site, here, up) => { case CoreParamsKey => CoreParams() })
  val platformOnly = new Config((site, here, up) => { case PlatformParamsKey => PlatformParams() })
  assert((new CoreConsumer()(coreOnly)).FETCH_QUEUE_DEPTH == 4)
  assert((new PlatformConsumer()(platformOnly)).RESET_VECTOR == 0x200)
  println("Parameter defaults, composition and validation passed")
}
