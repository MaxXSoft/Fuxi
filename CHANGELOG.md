# Changelog

All notable changes to the Fuxi will be documented in this file.

## Unreleased

### Added

- Added RV32C integer compressed instruction support.
- Added halfword-aligned mixed-width instruction fetching.
- Added a configurable decoupled instruction fetch queue.
- Added split-page fetch handling with precise fault addresses.
- Added reusable test infrastructure and its regressions.
- Added a speculative fetch return-address stack with flush-based recovery.
- Added configurable 64-bit HPM counters with 44 branch/RAS, pipeline,
  cache, and translation events, plus counter access and inhibit controls.
- Added CSR, pipeline retirement, and local PMU event regressions.
- Added MPRV and MXR data-access privilege control.
- Added platform `time`/`timeh` CSR reads and a separate supervisor external interrupt input.
- Added RV32 `mstatush` as a writable WARL-zero CSR.

### Changed

- Reorganized core tests into stage and integration suites.
- Consolidated shared test fixtures and execution checkers.
- Updated CI and documentation for the new frontend and test layout.
- Extended `FuxiWrapper` with `rtc_time[63:0]` and `irq_sextern` inputs that platforms must drive.
- Made `mip.STIP` and `mip.SSIP` independent of machine timer/software inputs,
  requiring explicit forwarding by machine firmware.

### Fixed

- Fixed redirected fetches using stale translation contexts.
- Fixed instruction refill faults attaching to unrelated lookups.
- Fixed page-walk access faults attaching to unrelated lookups.
- Fixed data accesses and traps using stale privilege state during an older CSR write.
- Fixed SATP commits to refetch younger instructions under the new address space.
- Fixed uncached MMIO accesses to preserve byte addresses and byte/halfword/word transfer sizes.

## 0.0.2 - 2026-09-25

### Added

- Added regressions for BPU, buses, MMU/TLB, CSR, LSU/MDU, ROM, pipeline, and full-core execution.
- Added CLINT and GPIO AXI testbenches with a shared runner.
- Expanded CI to generate and lint Verilog and run the full test suite.

### Changed

- Upgraded to Chisel 7.13.0, Scala 2.13.18, and sbt 1.12.11.
- Migrated tests to Chisel 7 simulator APIs and modularized `CoreTest`.
- Updated the README and ignored BSP and Verilator build outputs.

### Fixed

- Fixed AXI/cache error, backpressure, completion, flush, AMO request, and refill invalidation handling.
- Fixed MMU page-walk tracking, permissions, `SFENCE.VMA`, and fault/fence interactions.
- Fixed AMO values, LR/SC reservations, fault classification, and permission checks.
- Fixed `xRET`/interrupt ordering, delegation filtering, `SEIP`, and counter permissions.
- Fixed CSR hazards, retirement accounting, branch training, and divider reuse.
- Fixed CLINT and GPIO AXI address and response handling.
- Pinned Verilator 5.048 to avoid incorrect CSR/`xRET` simulation in CI.

## 0.0.1 - 2021-06-28
