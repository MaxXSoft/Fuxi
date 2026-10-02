# Performance counters

Fuxi implements `mcycle`, `minstret`, and by default eight programmable 64-bit
HPM counters (`mhpmcounter3` through `mhpmcounter10`). Select one event ID below
in each `mhpmeventN`. These event IDs form the Fuxi event ABI; they are not SBI
PMU event IDs or counter numbers.

Generate another hardware configuration with:

```sh
sbt 'runMain Fuxi --hpm-counters 4 --target-dir build/hpm4'
```

Counts from 0 to 29 are supported. This changes the number of physical counters,
not the event IDs. There is no overflow interrupt, privilege filtering, or SBI
PMU firmware mapping. TIME/TIMEH remain unimplemented. Hardware performance
statistics here do not establish FPGA/ASIC area, timing, frequency, or power.

## CSR interface

| CSR | Address | Behavior |
| --- | --- | --- |
| `mcycle`, `mcycleh` | `0xb00`, `0xb80` | Writable cycle counter halves |
| `minstret`, `minstreth` | `0xb02`, `0xb82` | Writable retirement counter halves |
| `mhpmcounterN`, `mhpmcounterNh` | `0xb00+N`, `0xb80+N` | Writable HPM halves, N=3..31 |
| `cycle[h]`, `instret[h]`, `hpmcounterN[h]` | corresponding machine address + `0x100` | Read-only views |
| `mhpmeventN` | `0x320+N` | One event ID; 0 disables, unsupported values become 0 |
| `mcountinhibit` | `0x320` | Bit 0 freezes cycles, bit 2 freezes instret, bit N freezes HPM N |
| `mcounteren` | `0x306` | Corresponding bit grants S-mode read access |
| `scounteren` | `0x106` | U-mode reads require both enable registers |

All state resets to zero. Counter enable controls access, independently of
counting. M-mode can read the user aliases regardless of enable bits. Bit 1
(TIME) and bits for absent HPM counters are hardwired zero in all three control
registers. All absent machine counters/selectors are readable and writable as
zero; absent user views read zero in M-mode and trap in S/U because their enable
bits cannot be set. User aliases reject writes even in M-mode. Event high-half
CSRs are absent because Sscofpmf is not implemented.

Each counter increments at most once per cycle and wraps modulo 2^64. A write
to either half overrides the entire counter's increment on that edge (including
carry), preserving the other half. Inhibit/selector writes use their old values
for the committing edge and their new values thereafter. Thus the instruction
that freezes `minstret` counts; the instruction that unfreezes it does not.
Counter reads drain older writes to either half/alias and relevant control
CSRs; instret/HPM reads also drain older retirements.

Writing counter CSRRS/CSRRC forms (including immediate forms) calculate the
addressed 32-bit replacement from the same Execute snapshot returned to `rd`,
then explicitly write that half at retirement. Changes to that half between
Execute and WriteBack are overwritten; the other half retains its WriteBack
value. Freeze a counter before adjusting it when preserving counts matters.

For one consistent snapshot, freeze all wanted counters with one
`mcountinhibit` write, then read their halves. Set selectors and clear counters
while frozen before unfreezing. Include a compiler memory barrier around the
measurement region. Reading a running RV32 counter requires high-low-high reads
and retry if the high half changes. Freezing does not prevent explicit writes.

## Event IDs

Events 1–17 count **retired instructions**. Prediction metadata is captured at
Fetch/Decode and carried through the pipeline; stalled instructions count once,
and synchronous faults, interrupted instructions and squashed paths do not
count. Legal xRET/fence instructions still retire, although they do not belong
to the branch/JAL/JALR groups below.

| ID | Name | Meaning |
| --- | --- | --- |
| 1 | branch | Conditional branch |
| 2 | branch_miss | Conditional branch with direction or target error |
| 3 | branch_direction_miss | Final predicted taken differs from actual taken |
| 4 | branch_target_miss | Predicted and actual taken, but targets differ |
| 5 | jal | Direct jump, including C.J/C.JAL |
| 6 | jal_miss | Direct jump prediction error |
| 7 | jalr | Indirect jump, including C.JR/C.JALR |
| 8 | jalr_miss | Indirect jump prediction error |
| 9 | ras_candidate | JALR with architectural x1/x5 pop hint, including pop/push |
| 10 | ras_used | JALR actually predicted from the nonempty RAS at Fetch |
| 11 | ras_miss | RAS-used JALR prediction error |
| 12 | ras_fallback_miss | RAS candidate predicted without RAS and mispredicted |
| 13 | other_jalr | JALR without a RAS pop hint |
| 14 | other_jalr_miss | Prediction error in event 13 |
| 15 | compressed | 16-bit instruction |
| 16 | load | Integer load instruction (excludes LR/AMO) |
| 17 | store | Integer store instruction (excludes SC/AMO) |

Prediction errors follow Decode's direction/target comparison. Event 3 is the
**final predictor direction**, including BTB gating; it is not isolated PHT
accuracy. Direction and target errors are disjoint, so 2 = 3 + 4. A taken
prediction to the sequential address can still have a direction error.

Events 18–29 count microarchitectural work and can include speculative paths.

| ID | Name | Meaning |
| --- | --- | --- |
| 18 | frontend_empty | Decode not stalled, no flush, IF/ID has no valid instruction |
| 19 | load_use_stall | Raw load-use hazard cycles, excluding global flush |
| 20 | mdu_stall | Raw ALU/MDU stall request cycles, excluding global flush |
| 21 | mem_stall | Raw MEM stall request cycles, including cache maintenance/CSR busy, excluding global flush |
| 22 | csr_stall | Raw CSR hazard cycles, excluding global flush |
| 23 | fetch_queue_full | Fetch has an instruction but IF/ID refuses it, excluding flush |
| 24 | decode_redirect | Accepted Decode prediction recovery, excluding global flush |
| 25 | context_flush | Global trap/xRET/fence recovery cycles |
| 26 | ras_overflow | Accepted push-only on full RAS discards oldest entry |
| 27 | ras_empty_pop | Accepted empty-stack pop, including pop/push |
| 28 | ras_clear_mispredict | Decode recovery clears nonempty RAS |
| 29 | ras_clear_context | Global recovery clears nonempty RAS |

The four stall sources can overlap; do not add them into a CPI decomposition.
RAS update events honor flush priority. Full-stack pop/push replaces the top
and is not overflow. A clear has one cause: global recovery wins over Decode.
Empty RAS is not itself a misprediction; BTB fallback may be correct.
Decode recovery also includes stale taken predictions for ordinary instructions,
so event 24 need not equal the sum of retired branch/JAL/JALR misses.

| ID | Name | Meaning |
| --- | --- | --- |
| 30 | icache_access | Completed I-cache SRAM transfers |
| 31 | icache_miss | New I-cache misses launched from idle |
| 32 | icache_busy | Non-idle I-cache cycles |
| 33 | icache_walk_access | Event 30 from I-MMU page-table traffic |
| 34 | icache_walk_miss | Event 31 from I-MMU page-table traffic |
| 35 | dcache_access | Completed D-cache SRAM transfers (reads and writes) |
| 36 | dcache_miss | New D-cache misses launched from idle |
| 37 | dcache_busy | Non-idle D-cache cycles |
| 38 | dcache_writeback | Accepted AXI write-address bursts, eviction or maintenance |
| 39 | dcache_walk_access | Event 35 from D-MMU page-table traffic |
| 40 | dcache_walk_miss | Event 36 from D-MMU page-table traffic |
| 41 | itlb_miss | I-MMU page walk launched |
| 42 | itlb_walk_cycle | I-MMU address/read/update/access-fault states |
| 43 | dtlb_miss | D-MMU page walk launched |
| 44 | dtlb_walk_cycle | D-MMU address/read/update/access-fault states |

Cache access is specifically `en && valid`, including speculative transfers and
fault responses only if the requester keeps `en` asserted. The LSU withdraws
`en` when taking a data access fault, so that demand fault is not a completed
D-cache transfer here; a page walker can retain its enable through a fault.
I-cache accesses count 32-bit fetch words rather than instructions.
Cache totals include page walks;
subtract the walk subset to obtain program-side traffic. Uncached transfers
bypass these cache counters. Refill retry produces one access completion and
no second miss. A dirty eviction plus refill is one miss and one writeback.
Busy cycles include completion/fault states and D-cache maintenance; walker
cycles exclude the launch edge and flush state, but include canceled work until
it drains. Cache/walker busy cycles overlap with each other and pipeline stalls.

A miss is counted at launch and an access at completion. Interval boundaries,
faults or canceled work can separate them. Report `misses/completed accesses`
with that qualification rather than treating it as a retired-instruction hit
rate. Subtracting walker subsets requires simultaneous totals and subsets, or
separate runs shown to be comparable.

## Derived metrics and benchmark use

For counters frozen over the same interval:

* IPC = instret / cycle; CPI = cycle / instret.
* Conditional prediction accuracy = 1 − event2 / event1.
* Conditional branch MPKI = 1000 × event2 / instret.
* Overall control-flow MPKI = 1000 × (event2 + event6 + event8) / instret.
* RAS coverage = event10 / event9.
* RAS-used accuracy = 1 − event11 / event10.
* RAS-candidate MPKI = 1000 × (event11 + event12) / instret.
* Cache MPKI = 1000 × event31 or event36 / instret.

Report a zero denominator as unavailable. With eight counters, use separate
event groups and repeat deterministic workloads; retain each group's own
cycle/instret and raw counts. Do not sum events from different runs as if they
were measured together. Measurement setup, function entry/exit, serialization,
and the stop instruction can contribute overhead. Print only after freezing.
The companion `verilator-axi-testbench` Fuxi benchmark supports this workflow;
keep its generated runs and reports under ignored `build/` or `debug/` paths.

The register behavior follows the [RISC-V machine hardware performance monitor
and counter control specification](https://github.com/riscv/riscv-isa-manual/blob/main/src/priv/machine.adoc).
Event meanings above are Fuxi-specific. Linux `perf` integration additionally
requires SBI PMU/platform mappings and has not been validated by these tests.
