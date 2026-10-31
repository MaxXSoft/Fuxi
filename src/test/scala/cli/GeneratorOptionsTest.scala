package cli

import org.scalatest.funsuite.AnyFunSuite

import configs._
import org.chipsalliance.cde.config.Config

class TestPreset extends Config(
  new WithHpmCounters(2) ++
    new WithRasEntries(3) ++
    new DefaultConfig
)

class NotAConfig
class NeedsArgument(n: Int) extends DefaultConfig
class PrivateConstructor private () extends DefaultConfig

class BrokenConstructor extends DefaultConfig {
  throw new IllegalArgumentException("broken preset")
}

class IncompleteConfig extends Config((site, here, up) => {
  case PlatformParamsKey => PlatformParams()
})

class GeneratorOptionsTest extends AnyFunSuite {
  test("parse presets, overrides and forwarded generator arguments") {
    def parsed(args: String*): GeneratorOptions = GeneratorOptions.parse(args).fold(
      exit => throw new AssertionError(exit.message), identity)
    def parameters(args: String*) = parsed(args: _*).config.resolve().fold(
      error => throw new AssertionError(error), identity)
    def rejects(args: String*): Unit = assert(
      GeneratorOptions.parse(args).exists(_.config.resolve().isLeft) ||
        GeneratorOptions.parse(args).left.exists(_.status != 0), args.mkString(" "))

    val defaults = parameters()
    assert(defaults(CoreParamsKey) == CoreParams())
    assert(parameters("--config", "configs.SmallConfig")(CoreParamsKey).hpmCounters == 0)
    assert(parameters("--config", "cli.TestPreset")(CoreParamsKey).bpu.rasEntries == 3)
    val before = parameters("--hpm-counters", "5", "--config", "cli.TestPreset")
    val after = parameters("--config=cli.TestPreset", "--hpm-counters=5")
    assert(before(CoreParamsKey) == after(CoreParamsKey))
    assert(after(CoreParamsKey).hpmCounters == 5 && after(CoreParamsKey).bpu.rasEntries == 3)
    val overridden = parameters("--config", "SmallConfig", "--fetch-queue-depth", "1",
      "--fetch-buffer-entries", "8", "--ghr-bits", "2", "--btb-entries", "8", "--ras-entries", "1",
      "--icache-lines", "2", "--dcache-lines", "4", "--cache-line-bytes", "8",
      "--itlb-entries", "2", "--dtlb-entries", "4", "--mul-latency", "0",
      "--reset-vector", "0x80000000", "--hart-id", "0xffffffff")
    assert(overridden(CoreParamsKey) == CoreParams(FrontendParams(1, 8), BpuParams(2, 8, 1),
      CacheParams(2, 8), CacheParams(4, 8), MmuParams(2, 4), 0, 0))
    assert(overridden(PlatformParamsKey).resetVector == BigInt("80000000", 16))
    assert(overridden(PlatformParamsKey).hartId == BigInt("ffffffff", 16))
    val forwarded = parsed("--target-dir", "directory with spaces", "--", "--firtool-option", "-O=debug")
    assert(forwarded.targetDir.contains("directory with spaces"))
    assert(forwarded.stageArgs == Seq("--firtool-option", "-O=debug"))
    assert(GeneratorOptions.parse(Seq("--help")).left.exists(_.status == 0))
    assert(parsed("--dump-config").dumpConfig)
    assert(ConfigDump(after).contains("hpmCounters = 5"))
    for (name <- Seq("DoesNotExist", "cli.NotAConfig", "cli.NeedsArgument",
                      "cli.PrivateConstructor", "cli.BrokenConstructor", "cli.IncompleteConfig")) {
      rejects("--config", name)
    }
    rejects("--hpm-counters"); rejects("--hpm-counters", "--dump-config")
    rejects("--hpm-counters", "0", "--hpm-counters=1")
    rejects("--config", "SmallConfig", "--config", "DefaultConfig")
    rejects("--hpm-counters", "false"); rejects("--hpm-counters", "-1")
    rejects("--hpm-counters", "30"); rejects("--hpm-counters", "2147483648")
    rejects("--fetch-buffer-entries", "3"); rejects("--cache-line-bytes", "4")
    rejects("--reset-vector", "0x202"); rejects("--reset-vector", "0x100000000")
    rejects("--hart-id", "-1"); rejects("--unknown"); rejects("unexpected-positional")
    rejects("--target-dir", "--dump-config")
  }
}
