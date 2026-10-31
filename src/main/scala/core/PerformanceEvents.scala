package core

import chisel3._

import io._
import consts.PerfEvent._
import consts.PerfEvent.{Retired => R, Microarchitecture => U, Memory => M}

// Purely combinational observation: retirement gates architectural events;
// other sources include speculative work and overlapping waits.
class PerformanceEvents extends Module {
  val io = IO(new Bundle {
    // retiring instruction
    val retired        = Input(Bool())
    val retire         = Input(new RetirePerformanceIO)
    // pipeline activity
    val flush          = Input(Bool())
    val flushIf        = Input(Bool())
    val stallId        = Input(Bool())
    val fetchValid     = Input(Bool())
    val fetchReady     = Input(Bool())
    val decodeValid    = Input(Bool())
    val decodeRedirect = Input(Bool())
    val loadHazard     = Input(Bool())
    val csrHazard      = Input(Bool())
    val aluStall       = Input(Bool())
    val memStall       = Input(Bool())
    // local subsystem events
    val ras            = Input(new RasPerformanceIO)
    val bus            = Input(new BusPerformanceIO)
    // grouped events for CSR counters
    val events         = Output(new PerformanceEventsIO)
  })

  // architectural events
  val retiredEvents = io.events(Group.RETIRED)
  val retired = io.retired
  val p = io.retire
  retiredEvents(R.BRANCH.id) := retired && p.branch
  retiredEvents(R.BRANCH_MISS.id) := retired && p.branch && p.miss
  retiredEvents(R.BRANCH_DIRECTION_MISS.id) := retired && p.branch && p.directionMiss
  retiredEvents(R.BRANCH_TARGET_MISS.id) := retired && p.branch && p.targetMiss
  retiredEvents(R.JAL.id) := retired && p.jal
  retiredEvents(R.JAL_MISS.id) := retired && p.jal && p.miss
  retiredEvents(R.JALR.id) := retired && p.jalr
  retiredEvents(R.JALR_MISS.id) := retired && p.jalr && p.miss
  retiredEvents(R.RAS_CANDIDATE.id) := retired && p.jalr && p.rasCandidate
  retiredEvents(R.RAS_USED.id) := retired && p.jalr && p.rasUsed
  retiredEvents(R.RAS_MISS.id) := retired && p.jalr && p.rasUsed && p.miss
  retiredEvents(R.RAS_FALLBACK_MISS.id) := retired && p.jalr && p.rasCandidate && !p.rasUsed && p.miss
  retiredEvents(R.OTHER_JALR.id) := retired && p.jalr && !p.rasCandidate
  retiredEvents(R.OTHER_JALR_MISS.id) := retired && p.jalr && !p.rasCandidate && p.miss
  retiredEvents(R.COMPRESSED.id) := retired && p.compressed
  retiredEvents(R.LOAD.id) := retired && p.load
  retiredEvents(R.STORE.id) := retired && p.store

  // microarchitecture events
  val microEvents = io.events(Group.MICROARCHITECTURE)
  microEvents(U.FRONTEND_EMPTY.id) := !io.flushIf && !io.stallId && !io.decodeValid
  microEvents(U.LOAD_USE_STALL.id) := !io.flush && io.loadHazard
  microEvents(U.MDU_STALL.id) := !io.flush && io.aluStall
  microEvents(U.MEM_STALL.id) := !io.flush && io.memStall
  microEvents(U.CSR_STALL.id) := !io.flush && io.csrHazard
  microEvents(U.FETCH_QUEUE_FULL.id) := !io.flushIf && io.fetchValid && !io.fetchReady
  microEvents(U.DECODE_REDIRECT.id) := !io.flush && io.decodeRedirect
  microEvents(U.CONTEXT_FLUSH.id) := io.flush
  microEvents(U.RAS_OVERFLOW.id) := io.ras.overflow
  microEvents(U.RAS_EMPTY_POP.id) := io.ras.emptyPop
  microEvents(U.RAS_CLEAR_MISPREDICT.id) := io.ras.clearNonempty && !io.flush
  microEvents(U.RAS_CLEAR_CONTEXT.id) := io.ras.clearNonempty && io.flush

  // memory subsystem events
  val memoryEvents = io.events(Group.MEMORY)
  memoryEvents(M.ICACHE_ACCESS.id) := io.bus.icache.access
  memoryEvents(M.ICACHE_MISS.id) := io.bus.icache.miss
  memoryEvents(M.ICACHE_BUSY.id) := io.bus.icache.busy
  memoryEvents(M.ICACHE_WALK_ACCESS.id) := io.bus.icacheWalkAccess
  memoryEvents(M.ICACHE_WALK_MISS.id) := io.bus.icacheWalkMiss
  memoryEvents(M.DCACHE_ACCESS.id) := io.bus.dcache.access
  memoryEvents(M.DCACHE_MISS.id) := io.bus.dcache.miss
  memoryEvents(M.DCACHE_BUSY.id) := io.bus.dcache.busy
  memoryEvents(M.DCACHE_WRITEBACK.id) := io.bus.dcache.writeback
  memoryEvents(M.DCACHE_WALK_ACCESS.id) := io.bus.dcacheWalkAccess
  memoryEvents(M.DCACHE_WALK_MISS.id) := io.bus.dcacheWalkMiss
  memoryEvents(M.ITLB_MISS.id) := io.bus.itlb.miss
  memoryEvents(M.ITLB_WALK_CYCLE.id) := io.bus.itlb.walkCycle
  memoryEvents(M.DTLB_MISS.id) := io.bus.dtlb.miss
  memoryEvents(M.DTLB_WALK_CYCLE.id) := io.bus.dtlb.walkCycle
}
