# Scala test support

Tests are executable `App`s, run with `sbt 'Test / runMain package.TestName'`.
`utils.TestDriver` runs the Chisel simulation and reports failures through the
process exit code. Shared fixtures live only in test sources. Reusable helpers
and their own tests are colocated in `utils`, including the Core harnesses and
CSR regression helpers.
Harnesses and reference models used by a single test stay in that test file.
The CI `Test Utilities` step runs the support tests, including
`utils.CoreWritebackLoopTest`, which validates writeback selection with a real Core.

## Programs and instruction images

Use `utils.CoreProgram` for ROM-backed programs. `emit32` appends 32 bits and
`emit16` appends 16 bits; both advance a byte-addressed `pc`. A 32-bit instruction
may start at a halfword boundary. `expectWriteback` records a checkpoint at the
current PC, before the following instruction is emitted. `finish()` appends an
32-bit x31 = 1 marker followed by a compressed `c.j 0` loop and returns the marker PC.

```scala
val program = new CoreProgram(utils.InstructionImage.CompressedNops)
program.emit16(InstEncoding.c_nop())
program.expectWriteback(10, 42)
program.emit32(InstEncoding.addi(10, 0, 42)) // starts at RESET_PC + 2
val donePc = program.finish()
// new CoreWrapper(program.words), then CoreProgramTester(..., donePc, maxCycles)
```

`seekWord(wordIndex)` and `pcAtWord(wordIndex)` use word indices relative to RESET_PC.
`seekAddress` uses an absolute, halfword-aligned address. Both seek methods only
move the cursor forward; gaps use the image fill pattern once code is placed
beyond them. Neither seek method emits instructions or updates reference counters. `place16` and
`place32` place out-of-line code without changing the cursor. Overlaps, invalid
widths and ROM overflow are rejected; illegal instruction *encodings* are
allowed so that trap tests can construct them. `emit32` remains overridable for
instruction-specific reference models such as the instret-write tests.

`utils.InstructionImage` provides the same little-endian packing without ROM
size or reset-PC constraints. It supports explicit `replace16`/`replace32` for
tests that rewrite memory, plus aligned `read32` and word-range export. Set the
fill pattern explicitly: zero for ordinary memory/page tables, `0x13` for
32-bit NOPs, or `InstructionImage.CompressedNops` for compressed NOPs. Keep
physical placement separate from virtual execution PCs in translation tests.

`utils.InstEncoding` provides mnemonic encoders for program stimuli, with
register/immediate/alignment validation. Compressed mnemonics use the `c_`
prefix; U-type immediates are unsigned 20-bit fields. HINT operands are preserved.
Illegal encodings, packed-byte expectations and independent decoder reference
vectors deliberately remain literal. `utils.InstEncodingTest` checks fixed
encoding vectors and invalid operands and is included in CI.

## Execution expectations and clock ownership

`CoreTraceProgram` adds convenience `half`/`word` methods that record retirement
expectations by default. Use `retires = false` for faulting or squashed code and
`event` to describe out-of-line execution, including repeated PCs. Placement
alone never infers dynamic execution. Its default gap fill is compressed NOPs.

- `WritebackChecker` checks selected `(PC, occurrence)` pairs in declaration
  order. Occurrences start at one and count write-enable events at that PC,
  not cycles or loop iterations. Repeated PCs are supported; duplicate pairs
  and decreasing occurrences at the same PC are rejected. Unselected visits
  (including visits after the last checkpoint) are allowed. Use
  `checkOccurrences(pc, total)` when the total number of writes also matters.
  `CoreProgramTester` supplies the simple clock loop around it.
- `RetirementChecker` checks every retirement, including repeated PCs. An
  expected `write = None` leaves writeback unchecked. Counter checking is
  optional because CSR writes can replace the retirement counter.
- `TrapChecker` checks trap PC/cause/value, missing events and extra events.
- `CoreChecks.writeback` also serves the external trace-file comparison, which
  still checks every non-x0 writeback rather than selected checkpoints.

`expectWriteback(rd, data, occurrence = 1)` registers a checkpoint at the current
PC. Register multiple occurrences before emitting a loop instruction, for example
`expectWriteback(10, 2, occurrence = 2)` and `expectWriteback(10, 5, occurrence = 5)`.
`CoreWritebackLoopTest` exercises this against an actual six-iteration core program.

`Checker` exposes diagnostics and completion; `EventChecker[A]` adds typed event
observation. `OrderedChecker[E, A]` privately owns the pending queue and shares
`missing`, `checkComplete` and validate-before-consume behavior. The concrete
checkers retain event selection, comparisons and domain-specific counters.

Checkers never step the clock. Tests sample retirement and `minstret` before
an edge, then step once so that the observed retirement updates the counter.
Trap and retirement streams are separate because they originate in different
pipeline stages. `checkComplete()` must run before a test succeeds.
`PeekPokeTester.runUntil` bounds iterations; its caller owns each clock edge.

## Harnesses and bus responses

`CoreMemoryHarness` wires Core/ROM/RAM and exposes memory/fence stall inputs,
request signals and fetch-queue occupancy. Always initialize both stall inputs.
`CoreWrapper` ties stalls off and preserves the small `DebugIO` interface for
simple programs and file-backed traces. Call `io.observation.connect(core)` from
the harness after binding the bundle through `IO` to connect the retirement,
counter and trap probes; MRET/SRET are excluded from trap events.
CoreBus integration tests keep their actual bus wiring and share these probes.

Mix in `utils.AxiTestSupport` for AXI input initialization and read-beat driving.
Its `AxiReadResponder` serves one outstanding 32-bit INCR burst, records accepted
requests, echoes IDs and supports per-beat responses. Call `beforeStep` before
the test's edge and `afterStep` once after it. Address acceptance and new beat
availability can be delayed independently; a presented response remains stable
under backpressure. Explicit flush schedules and bus-specific assertions belong
in each test. The atomic-memory read/write model remains test-specific.

`utils.CoreTestSupportTest` covers image boundaries and checker failure behavior;
`utils.AxiTestSupportTest` checks read-response ownership and backpressure. Both
are included in the existing CI test entry points.
