package cli

import configs._
import org.chipsalliance.cde.config.{Config, Parameters}
import scala.util.control.NonFatal
import scopt.OParser

case class ConfigOptions(
  preset: String = "DefaultConfig",
  fetchQueueDepth: Option[Int] = None,
  fetchBufferEntries: Option[Int] = None,
  ghrBits: Option[Int] = None,
  btbEntries: Option[Int] = None,
  rasEntries: Option[Int] = None,
  icacheLines: Option[Int] = None,
  dcacheLines: Option[Int] = None,
  cacheLineBytes: Option[Int] = None,
  itlbEntries: Option[Int] = None,
  dtlbEntries: Option[Int] = None,
  mulLatency: Option[Int] = None,
  hpmCounters: Option[Int] = None,
  resetVector: Option[BigInt] = None,
  hartId: Option[BigInt] = None
) {
  def resolve(): Either[String, Parameters] = ConfigLoader.load(preset).flatMap { base =>
    try {
      val fragments: Seq[Option[Config]] = Seq(
        fetchQueueDepth.map(new WithFetchQueueDepth(_)),
        fetchBufferEntries.map(new WithFetchBufferEntries(_)),
        ghrBits.map(new WithGhrBits(_)), btbEntries.map(new WithBtbEntries(_)),
        rasEntries.map(new WithRasEntries(_)), icacheLines.map(new WithICacheLines(_)),
        dcacheLines.map(new WithDCacheLines(_)), cacheLineBytes.map(new WithCacheLineBytes(_)),
        itlbEntries.map(new WithITlbEntries(_)), dtlbEntries.map(new WithDTlbEntries(_)),
        mulLatency.map(new WithMulLatency(_)), hpmCounters.map(new WithHpmCounters(_)),
        resetVector.map(new WithResetVector(_)), hartId.map(new WithHartId(_))
      )
      val parameters = fragments.flatten.foldRight(base)((fragment, up) => fragment.orElse(up))
      ConfigValidation(parameters)
      Right(parameters)
    } catch { case NonFatal(e) => Left(s"Invalid configuration: ${e.getMessage}") }
  }
}

object ConfigOptions {
  // Generator and integration-test CLIs share exactly the same override schema.
  def parser[C](get: C => ConfigOptions, set: (C, ConfigOptions) => C): OParser[_, C] = {
    val builder = OParser.builder[C]
    import builder._
    def int(name: String, description: String)(update: (ConfigOptions, Int) => ConfigOptions) =
      opt[Int](name).maxOccurs(1).valueName("<n>").text(description)
        .action((value, options) => set(options, update(get(options), value)))
    OParser.sequence(
      opt[String]("config").maxOccurs(1).valueName("<class>")
        .text("Preset class (default: configs.DefaultConfig); short names use the configs package")
        .action((value, options) => set(options, get(options).copy(preset = value))),
      int("fetch-queue-depth", "IF/ID instruction queue entries")((c, n) => c.copy(fetchQueueDepth = Some(n))),
      int("fetch-buffer-entries", "Fetch word buffer entries (power of two, >= 2)")((c, n) => c.copy(fetchBufferEntries = Some(n))),
      int("ghr-bits", "Global branch history bits (PHT has 2^n entries)")((c, n) => c.copy(ghrBits = Some(n))),
      int("btb-entries", "BTB entries (power of two, >= 2)")((c, n) => c.copy(btbEntries = Some(n))),
      int("ras-entries", "Return address stack entries")((c, n) => c.copy(rasEntries = Some(n))),
      int("icache-lines", "Instruction cache line count")((c, n) => c.copy(icacheLines = Some(n))),
      int("dcache-lines", "Data cache line count")((c, n) => c.copy(dcacheLines = Some(n))),
      int("cache-line-bytes", "Bytes per line in both caches (power of two, 8..1024)")((c, n) => c.copy(cacheLineBytes = Some(n))),
      int("itlb-entries", "Instruction TLB entries")((c, n) => c.copy(itlbEntries = Some(n))),
      int("dtlb-entries", "Data TLB entries")((c, n) => c.copy(dtlbEntries = Some(n))),
      int("mul-latency", "Multiplier pipeline latency (>= 0)")((c, n) => c.copy(mulLatency = Some(n))),
      int("hpm-counters", "HPM counter count, excluding cycle/instret (0..29)")((c, n) => c.copy(hpmCounters = Some(n))),
      opt[BigInt]("reset-vector").maxOccurs(1).valueName("<address>")
        .text("Word-aligned RV32 reset address, decimal or 0x hexadecimal")
        .action((n, c) => set(c, get(c).copy(resetVector = Some(n)))),
      opt[BigInt]("hart-id").maxOccurs(1).valueName("<id>").text("Unsigned 32-bit mhartid")
        .action((n, c) => set(c, get(c).copy(hartId = Some(n))))
    )
  }
}
