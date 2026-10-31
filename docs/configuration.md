# Processor configuration

Fuxi uses CDE (`org.chipsalliance.cde.config`) for elaboration-time parameters.
The implementation lives in `src/main/scala/configs`; command-line parsing uses
scopt in `src/main/scala/cli`. Parameters describe generated hardware, not runtime
CSR settings. The default configuration preserves the previous hardware.

## Presets and generation

```sh
sbt 'runMain Fuxi --target-dir verilog/build'
sbt 'runMain Fuxi --config SmallConfig --hpm-counters 4 --target-dir verilog/small/build'
sbt 'runMain Fuxi --config ProfilingConfig --dump-config'
sbt 'runMain Fuxi --help'
```

`--config SmallConfig` means `configs.SmallConfig`. A fully qualified name selects
a class from another package on the sbt runtime classpath. The class must extend
CDE `Config` and expose a public, no-argument JVM constructor. A Scala constructor
with default arguments does not supply that constructor. Missing classes, wrong
types, constructor failures and invalid parameter values are errors.

| Parameter | DefaultConfig | SmallConfig | ProfilingConfig |
| --- | ---: | ---: | ---: |
| IF/ID instruction queue | 4 | 2 | 4 |
| Fetch word buffer | 4 | 4 | 4 |
| GHR bits / PHT entries | 5 / 32 | 4 / 16 | 5 / 32 |
| BTB entries | 64 | 32 | 64 |
| RAS entries | 16 | 8 | 16 |
| I-cache lines / capacity | 64 / 4 KiB | 16 / 1 KiB | 64 / 4 KiB |
| D-cache lines / capacity | 128 / 8 KiB | 32 / 2 KiB | 128 / 8 KiB |
| Cache line bytes | 64 | 64 | 64 |
| I-TLB / D-TLB entries | 16 / 16 | 8 / 8 | 16 / 16 |
| Multiplier latency | 1 | 1 | 1 |
| HPM counters (excluding cycle/instret) | 8 | 0 | 29 |

All presets use reset vector `0x200`, hart ID `0`, and the data uncached range
`[0x10000000, 0x20000000)`. `SmallConfig` is a smaller resource configuration;
these names do not claim measured timing or performance improvements.

Only explicitly supplied CLI options override the preset, regardless of where
`--config` appears. Singleton options cannot be repeated. Both `--option value`
and `--option=value` work. `--dump-config` prints the effective parameters,
including derived capacities, and exits without generating RTL.

| Option | CDE fragment | Constraint |
| --- | --- | --- |
| `--fetch-queue-depth` | `WithFetchQueueDepth` | Positive instruction count |
| `--fetch-buffer-entries` | `WithFetchBufferEntries` | Power of two, at least 2 words |
| `--ghr-bits` | `WithGhrBits` | 2..30; PHT contains 2^n counters |
| `--btb-entries` | `WithBtbEntries` | Power of two, at least 2 |
| `--ras-entries` | `WithRasEntries` | Positive, less than Int.MaxValue; need not be a power of two |
| `--icache-lines`, `--dcache-lines` | `WithICacheLines`, `WithDCacheLines` | Power of two, at least 2 |
| `--cache-line-bytes` | `WithCacheLineBytes` | Power of two, 8..1024, applied to both caches |
| `--itlb-entries`, `--dtlb-entries` | `WithITlbEntries`, `WithDTlbEntries` | Power of two, at least 2 |
| `--mul-latency` | `WithMulLatency` | Nonnegative pipeline latency |
| `--hpm-counters` | `WithHpmCounters` | 0..29 |
| `--reset-vector` | `WithResetVector` | Word-aligned unsigned RV32 address |
| `--hart-id` | `WithHartId` | Unsigned 32-bit value |

Address values accept decimal and `0x` hexadecimal. Cache geometry must leave a
positive tag width and fit the implementation's integer word count. The checks
describe valid geometry, not a promise that very large memories will fit on a
particular FPGA or host.

Unknown Fuxi arguments are rejected. Pass additional Chisel/firtool arguments
after `--`, for example:

```sh
sbt 'runMain Fuxi --target-dir verilog/build -- --firtool-option -O=debug'
```

`make` remains the default-configuration build. Use separate target directories
for custom configurations, so a subsequent default build does not reuse custom
RTL by timestamp.

## Defining a configuration

```scala
package configs

import org.chipsalliance.cde.config.Config

class MyConfig extends Config(
  new WithFetchQueueDepth(8)
    .orElse(new WithICacheLineBytes(32))
    .orElse(new WithDCacheLineBytes(64))
    .orElse(new WithDataUncachedRegion(BigInt("f0000000", 16), BigInt(1) << 32))
    .orElse(new DefaultConfig)
)
```

Leftmost fragments take precedence. Each fragment uses `up` and `copy` to modify
one field while preserving the other fields from lower-priority configurations.
`WithICacheLineBytes` and `WithDCacheLineBytes` provide independent line sizes;
`WithDataUncachedRegion(start, end)` sets a single half-open physical range for
data routing. It does not define a general PMA map or instruction-cache policy.

`CoreParamsKey` contains frontend, predictor, cache, MMU, multiplier and counter
parameters. `PlatformParamsKey` contains reset, hart identity and uncached routing.
Fields have no implicit fallback; presets supply the base values. Fixed RV32,
instruction and bus widths remain in `consts.Architecture`.

The generator resolves and validates the final CDE environment before calling
Chisel, then creates `new Fuxi()(parameters)`. Configurable modules and bundles
receive that same environment through `implicit p: Parameters`. Core's queue
depth and HPM count have no competing constructor arguments. Reusable leaves
such as `TLB(size)`, `ReturnAddressStack(depth)` and `CsrFile(hpmCount, hartId)`
still take explicit local values selected by their parents.

Test harnesses must pass the same environment to their Core, ROM and program
builder. Test sources offer `utils.DefaultTestConfig._` for tests that explicitly
want the baseline; production sources do not import a global default.
