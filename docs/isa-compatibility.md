# ISA support and limitations

Fuxi implements RV32IMAC with Zicsr and Zifencei, M/S/U privilege modes, and
Sv32 address translation. The C extension covers RV32 integer instructions;
floating-point instructions are not implemented.

The README lists the implementation's ISA baselines. The limitations below
apply in addition to those version numbers. Fuxi does not provide complete
Machine/Supervisor ISA 1.13 support or unrestricted shared-memory atomics.

## Instruction fences

FENCE.TSO and reserved FENCE modes use ordinary FENCE semantics. FENCE ignores
`rs1`/`rd`, and FENCE.I ignores `funct12`/`rs1`/`rd`, as required by
[RV32I](https://docs.riscv.org/reference/isa/unpriv/rv32.html) and
[Zifencei](https://docs.riscv.org/reference/isa/unpriv/zifencei.html).

FENCE.I writes back dirty data, invalidates the instruction cache and
synchronizes instruction fetch. Ordinary FENCE does not perform cache
maintenance; see the external-memory restrictions below.

## Privileged architecture

The following requirements of the
[Machine](https://docs.riscv.org/reference/isa/priv/machine.html) and
[Supervisor](https://docs.riscv.org/reference/isa/priv/supervisor.html)
architectures are not implemented:

* `mstatus.TVM` and `TSR` are hardwired to zero. Machine software cannot
  use them to intercept S-mode `satp` accesses, SFENCE.VMA or SRET.
* `menvcfg`, `menvcfgh` and `senvcfg` are absent;
  accessing them raises an illegal-instruction exception. FIOM control of
  lower-privilege I/O and memory ordering is not supported.

`mconfigptr` is read-only zero: no configuration structure is exposed through
that CSR. RV32 `medelegh` is writable zero because no high exception-delegation
bits are implemented. `scause` supports exception-code values 0–31.
`mstatus.TW` is writable; WFI completes immediately as a NOP in M/S/U mode,
including when TW is set, so it does not exceed the permitted time bound.

PMP is not implemented: the exposed PMP registers read as zero and ignore
writes, and physical accesses are not restricted by PMP. Zero PMP entries are
permitted by the architecture, but software requiring physical memory isolation
must account for this limitation.

Sv32 uses software-managed accessed/dirty bits (Svade). Software must handle
page faults to set leaf PTE A/D bits; hardware PTE updates are not supported.
Non-leaf PTEs must have D/A/U clear; setting any of these bits causes a page fault.
ASIDs are fixed to zero, and SFENCE.VMA invalidates all translations regardless
of its address and ASID operands. These are permitted implementation choices.
The hart is little-endian in all modes; `mstatush` is writable zero. MRET/SRET
clear MPRV when returning below M-mode.

## Atomics and external memory access

LR.W, SC.W and the nine RV32 AMO operations are supported for memory owned by
the Fuxi hart. The memory interface does not provide cache coherence or atomic
transactions with other bus masters:

* Writes from another hart or a DMA device do not invalidate LR/SC reservations.
* AMOs use separate read and write transactions. An external writer can
  interleave between them, causing lost updates or an invalid atomic result.
* The data cache is a write-back cache without a coherence interface. FENCE
  and atomic acquire/release bits do not make dirty cache lines visible to
  noncoherent devices or invalidate stale cached data.

A platform must prevent concurrent external access to memory used for LR/SC
and AMOs, or supply the missing synchronization/coherence mechanisms. DMA
buffers need a platform-specific ownership and cache-maintenance protocol;
ordinary FENCE is not a cache flush. Atomics must not be assumed to work on
arbitrary memory-mapped device registers.

The [A extension](https://docs.riscv.org/reference/isa/unpriv/a-st-ext.html)
does not require multiple harts. A single hart with exclusively owned memory
can meet its atomicity requirements. The restrictions above also apply to the
README's A 2.0 baseline; that version number does not authorize multi-master
sharing without additional platform support.
