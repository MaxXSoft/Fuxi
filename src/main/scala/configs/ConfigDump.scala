package configs

import org.chipsalliance.cde.config.Parameters

object ConfigDump {
  def apply(p: Parameters): String = {
    val c = p(CoreParamsKey)
    val platform = p(PlatformParamsKey)
    Seq(
      "frontend.fetchQueueDepth" -> c.frontend.fetchQueueDepth,
      "frontend.fetchBufferEntries" -> c.frontend.fetchBufferEntries,
      "bpu.ghrBits" -> c.bpu.ghrBits,
      "bpu.phtEntries" -> c.bpu.phtEntries,
      "bpu.btbEntries" -> c.bpu.btbEntries,
      "bpu.rasEntries" -> c.bpu.rasEntries,
      "icache.lines" -> c.icache.lines,
      "icache.lineBytes" -> c.icache.lineBytes,
      "icache.capacityBytes" -> c.icache.capacityBytes,
      "dcache.lines" -> c.dcache.lines,
      "dcache.lineBytes" -> c.dcache.lineBytes,
      "dcache.capacityBytes" -> c.dcache.capacityBytes,
      "mmu.itlbEntries" -> c.mmu.itlbEntries,
      "mmu.dtlbEntries" -> c.mmu.dtlbEntries,
      "mulLatency" -> c.mulLatency,
      "hpmCounters" -> c.hpmCounters,
      "platform.resetVector" -> s"0x${platform.resetVector.toString(16)}",
      "platform.hartId" -> platform.hartId,
      "platform.dataUncached.start" -> s"0x${platform.dataUncached.start.toString(16)}",
      "platform.dataUncached.endExclusive" -> s"0x${platform.dataUncached.end.toString(16)}"
    ).map { case (name, value) => s"$name = $value" }.mkString("\n")
  }
}
