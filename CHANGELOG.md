# Changelog

All notable changes to the Fuxi will be documented in this file.

## Unreleased

### Added

- Added RV32C integer compressed instruction support.
- Added halfword-aligned mixed-width instruction fetching.
- Added a configurable decoupled instruction fetch queue.
- Added split-page fetch handling with precise fault addresses.
- Added reusable test infrastructure and its regressions.

### Changed

- Reorganized core tests into stage and integration suites.
- Consolidated shared test fixtures and execution checkers.
- Updated CI and documentation for the new frontend and test layout.

### Fixed

- Fixed redirected fetches using stale translation contexts.
- Fixed instruction refill faults attaching to unrelated lookups.
- Fixed page-walk access faults attaching to unrelated lookups.

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
