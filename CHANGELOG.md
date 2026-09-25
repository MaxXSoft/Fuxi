# Changelog

All notable changes to the Fuxi will be documented in this file.

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
