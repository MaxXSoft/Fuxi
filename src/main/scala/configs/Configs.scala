package configs

import org.chipsalliance.cde.config.Config

// Public no-argument constructors are the entry points for --config reflection.
class DefaultConfig extends Config((site, here, up) => {
  case CoreParamsKey => CoreParams()
  case PlatformParamsKey => PlatformParams()
})

class SmallConfig extends Config(
  new WithICacheLines(16) ++
    new WithDCacheLines(32) ++
    new WithITlbEntries(8) ++
    new WithDTlbEntries(8) ++
    new WithGhrBits(4) ++
    new WithBtbEntries(32) ++
    new WithRasEntries(8) ++
    new WithFetchQueueDepth(2) ++
    new WithHpmCounters(0) ++
    new DefaultConfig
)

class ProfilingConfig extends Config(
  new WithHpmCounters(29) ++
    new DefaultConfig
)
