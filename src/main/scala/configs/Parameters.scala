package configs

import org.chipsalliance.cde.config.{Field, Parameters}

private[configs] object Geometry {
  def powerOfTwo(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0
  def indexBits(n: Int): Int = Integer.numberOfTrailingZeros(n)
}

case class FrontendParams(fetchQueueDepth: Int = 4, fetchBufferEntries: Int = 4) {
  require(fetchQueueDepth > 0, "fetchQueueDepth must be positive")
  require(fetchBufferEntries >= 2 && Geometry.powerOfTwo(fetchBufferEntries),
    "fetchBufferEntries must be a power of two, at least 2")
}

case class BpuParams(ghrBits: Int = 5, btbEntries: Int = 64, rasEntries: Int = 16) {
  require(ghrBits >= 2 && ghrBits <= 30, "ghrBits must be in 2..30")
  require(btbEntries >= 2 && Geometry.powerOfTwo(btbEntries),
    "btbEntries must be a power of two, at least 2")
  require(rasEntries > 0 && rasEntries < Int.MaxValue, "rasEntries must be positive and less than Int.MaxValue")
  def phtEntries: Int = 1 << ghrBits
  def btbIndexBits: Int = Geometry.indexBits(btbEntries)
  def btbTagBits: Int = consts.Architecture.ADDR_WIDTH - btbIndexBits - consts.Architecture.INST_ALIGN_WIDTH
}

case class CacheParams(lines: Int, lineBytes: Int = 64) {
  require(lines >= 2 && Geometry.powerOfTwo(lines), "cache lines must be a power of two, at least 2")
  require(lineBytes >= 8 && lineBytes <= 1024 && Geometry.powerOfTwo(lineBytes),
    "cache lineBytes must be a power of two in 8..1024 (2..256 AXI beats)")
  require(indexBits + offsetBits < consts.Architecture.ADDR_WIDTH, "cache must leave at least one tag bit")
  require(lines.toLong * (lineBytes / 4) <= Int.MaxValue, "cache word count exceeds the implementation limit")
  def indexBits: Int = Geometry.indexBits(lines)
  def offsetBits: Int = Geometry.indexBits(lineBytes)
  def capacityBytes: Long = lines.toLong * lineBytes
}

case class MmuParams(itlbEntries: Int = 16, dtlbEntries: Int = 16) {
  require(itlbEntries >= 2 && Geometry.powerOfTwo(itlbEntries), "itlbEntries must be a power of two, at least 2")
  require(dtlbEntries >= 2 && Geometry.powerOfTwo(dtlbEntries), "dtlbEntries must be a power of two, at least 2")
}

case class CoreParams(
  frontend: FrontendParams = FrontendParams(),
  bpu: BpuParams = BpuParams(),
  icache: CacheParams = CacheParams(64),
  dcache: CacheParams = CacheParams(128),
  mmu: MmuParams = MmuParams(),
  mulLatency: Int = 1,
  hpmCounters: Int = 8
) {
  require(mulLatency >= 0, "mulLatency must be nonnegative")
  require(hpmCounters >= 0 && hpmCounters <= 29, "hpmCounters must be in 0..29")
}

// This describes data-path routing, not a general PMA or instruction-cache policy.
case class AddressRange(start: BigInt, end: BigInt) {
  require(start >= 0 && start < end && end <= (BigInt(1) << 32),
    "uncached range must satisfy 0 <= start < end <= 2^32")
}

case class PlatformParams(
  resetVector: BigInt = BigInt(0x200),
  dataUncached: AddressRange = AddressRange(BigInt("10000000", 16), BigInt("20000000", 16)),
  hartId: BigInt = BigInt(0)
) {
  require(resetVector >= 0 && resetVector < (BigInt(1) << 32) && (resetVector & 3) == 0,
    "resetVector must be a word-aligned unsigned 32-bit address")
  require(hartId >= 0 && hartId < (BigInt(1) << 32), "hartId must be an unsigned 32-bit value")
}

case object CoreParamsKey extends Field[CoreParams]
case object PlatformParamsKey extends Field[PlatformParams]

object ConfigValidation {
  // Force both queries before elaboration, including constructor checks in fragments.
  def apply(p: Parameters): Unit = {
    require(p(CoreParamsKey) != null, "CoreParamsKey must be defined")
    require(p(PlatformParamsKey) != null, "PlatformParamsKey must be defined")
  }
}
