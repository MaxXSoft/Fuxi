package configs

import org.chipsalliance.cde.config.Config

// Public no-argument constructors are the entry points for --config reflection.
class DefaultConfig extends Config((site, here, up) => {
  case CoreParamsKey => CoreParams()
  case PlatformParamsKey => PlatformParams()
})

class SmallConfig extends Config(
  new WithICacheLines(16)
    .orElse(new WithDCacheLines(32))
    .orElse(new WithITlbEntries(8))
    .orElse(new WithDTlbEntries(8))
    .orElse(new WithGhrBits(4))
    .orElse(new WithBtbEntries(32))
    .orElse(new WithRasEntries(8))
    .orElse(new WithFetchQueueDepth(2))
    .orElse(new WithHpmCounters(0))
    .orElse(new DefaultConfig)
)

class ProfilingConfig extends Config(new WithHpmCounters(29).orElse(new DefaultConfig))
