package core

import chisel3._
import chisel3.util.Queue

import io._
import consts.Parameters._
import utils._
import csr.CsrFile
import lsu.ExclusiveMonitor
import consts.Paging.PPN_WIDTH
import consts.PerfEvent._
import consts.PerfEvent.{Retired => R, Microarchitecture => U, Memory => M}
import consts.CSR.CSR_MODE_S

class Core(val fetchQueueDepth: Int, val hpmCount: Int) extends Module {
  val io = IO(new Bundle {
    // interrupt request
    val irq     = new InterruptIO
    val rtcTime = Input(UInt(RTC_WIDTH.W))
    // TLB/cache control
    val tlb     = new TlbControlIO(PPN_WIDTH)
    val cache   = new CacheControlIO
    // ROM interface (I-cache)
    val rom     = new SramIO(ADDR_WIDTH, INST_WIDTH)
    // RAM interface (D-cache)
    val ram     = new SramIO(ADDR_WIDTH, DATA_WIDTH)
    // for trace generating
    val debug   = new DebugIO
    // performance monitoring
    val pmu     = Input(new BusPerformanceIO)
  })

  // all stages
  val fetch   = Module(new Fetch)
  val ifid    = Module(new Queue(new FetchIO, fetchQueueDepth,
                                 pipe = true, flow = true, hasFlush = true))
  val decoder = Module(new Decoder)
  val idex    = Module(new MidStage(new DecoderIO))
  val alu     = Module(new ALU)
  val exmem   = Module(new MidStage(new AluIO))
  val mem     = Module(new core.Mem)
  val memwb   = Module(new MidStage(new MemIO))
  val wb      = Module(new WriteBack)

  // register file
  val regfile = Module(new RegFile)

  // CSR file
  val csrfile = Module(new CsrFile(hpmCount))

  // exclusive monitor
  val excMon  = Module(new ExclusiveMonitor)

  // hazard resolve & pipeline control
  val resolve = Module(new HazardResolver)
  val control = Module(new PipelineController)

  // fetch stage
  fetch.io.flush    := control.io.flushIf
  fetch.io.contextFlush := control.io.flush
  fetch.io.stall    := !ifid.io.enq.ready
  fetch.io.flushPc  := control.io.flushPc
  fetch.io.rom      <> io.rom
  fetch.io.branch   <> decoder.io.branch
  ifid.io.flush.get := control.io.flushIf
  ifid.io.enq.valid := fetch.io.fetch.valid
  ifid.io.enq.bits  := fetch.io.fetch
  ifid.io.deq.ready := !control.io.stallId

  // decoder stage
  decoder.io.fetch    := ifid.io.deq.bits
  decoder.io.fetch.valid := ifid.io.deq.valid
  decoder.io.flush    := control.io.flush
  decoder.io.stallId  := control.io.stallId
  decoder.io.read1    <> resolve.io.regRead1
  decoder.io.read2    <> resolve.io.regRead2
  idex.io.flush       := control.io.flush
  idex.io.stallPrev   := control.io.stallId || !ifid.io.deq.valid
  idex.io.stallNext   := control.io.stallEx
  idex.io.prev        <> decoder.io.decoder

  // execute stage
  alu.io.decoder      <> idex.io.next
  alu.io.flush        := control.io.flush
  alu.io.csrRead      <> resolve.io.csrRead
  exmem.io.flush      := control.io.flush
  exmem.io.stallPrev  := control.io.stallEx
  exmem.io.stallNext  := control.io.stallMm
  exmem.io.prev       <> alu.io.alu

  // memory accessing stage
  mem.io.alu          <> exmem.io.next
  mem.io.flush        := control.io.flush
  mem.io.ram          <> io.ram
  mem.io.csrHasInt    := csrfile.io.hasInt
  mem.io.csrBusy      := csrfile.io.busy
  mem.io.csrMode      := csrfile.io.mode
  mem.io.flushDcDone  := io.cache.flushDataDone
  mem.io.flushDcAccessFault := io.cache.flushDataAccessFault
  mem.io.excMon       <> resolve.io.check
  memwb.io.flush      := control.io.flushMemWb
  memwb.io.stallPrev  := control.io.stallMm
  memwb.io.stallNext  := control.io.stallWb
  memwb.io.prev       <> mem.io.mem

  // write back stage
  wb.io.mem     <> memwb.io.next
  wb.io.ramData := io.ram.rdata
  wb.io.debug   <> io.debug

  // register file
  regfile.io.read1      <> resolve.io.rf1
  regfile.io.read2      <> resolve.io.rf2
  regfile.io.write.en   := wb.io.reg.en
  regfile.io.write.addr := wb.io.reg.addr
  regfile.io.write.data := wb.io.reg.data

  // CSR
  csrfile.io.read           <> resolve.io.csr
  csrfile.io.write.op       := wb.io.csr.op
  csrfile.io.write.addr     := wb.io.csr.addr
  csrfile.io.write.data     := wb.io.csr.data
  csrfile.io.write.retired  := wb.io.csr.retired
  csrfile.io.irq            <> io.irq
  csrfile.io.rtcTime        := io.rtcTime
  csrfile.io.except         <> mem.io.except

  // Passive event collection. Retirement gates architectural events; the
  // other sources deliberately include speculative work and overlapping waits.
  val events = Wire(new PerformanceEventsIO)
  val retiredEvents = events(Group.RETIRED)
  val microEvents = events(Group.MICROARCHITECTURE)
  val memoryEvents = events(Group.MEMORY)
  val retired = wb.io.csr.retired
  val p = memwb.io.next.pmu
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
  microEvents(U.FRONTEND_EMPTY.id) := !control.io.flushIf && !control.io.stallId && !ifid.io.deq.valid
  microEvents(U.LOAD_USE_STALL.id) := !control.io.flush && resolve.io.loadFlag
  microEvents(U.MDU_STALL.id) := !control.io.flush && alu.io.stallReq
  microEvents(U.MEM_STALL.id) := !control.io.flush && mem.io.stallReq
  microEvents(U.CSR_STALL.id) := !control.io.flush && resolve.io.csrFlag
  microEvents(U.FETCH_QUEUE_FULL.id) := !control.io.flushIf && fetch.io.fetch.valid && !ifid.io.enq.ready
  microEvents(U.DECODE_REDIRECT.id) := !control.io.flush && decoder.io.flushIf
  microEvents(U.CONTEXT_FLUSH.id) := control.io.flush
  microEvents(U.RAS_OVERFLOW.id) := fetch.io.pmu.overflow
  microEvents(U.RAS_EMPTY_POP.id) := fetch.io.pmu.emptyPop
  microEvents(U.RAS_CLEAR_MISPREDICT.id) := fetch.io.pmu.clearNonempty && !control.io.flush
  microEvents(U.RAS_CLEAR_CONTEXT.id) := fetch.io.pmu.clearNonempty && control.io.flush
  memoryEvents(M.ICACHE_ACCESS.id) := io.pmu.icache.access
  memoryEvents(M.ICACHE_MISS.id) := io.pmu.icache.miss
  memoryEvents(M.ICACHE_BUSY.id) := io.pmu.icache.busy
  memoryEvents(M.ICACHE_WALK_ACCESS.id) := io.pmu.icacheWalkAccess
  memoryEvents(M.ICACHE_WALK_MISS.id) := io.pmu.icacheWalkMiss
  memoryEvents(M.DCACHE_ACCESS.id) := io.pmu.dcache.access
  memoryEvents(M.DCACHE_MISS.id) := io.pmu.dcache.miss
  memoryEvents(M.DCACHE_BUSY.id) := io.pmu.dcache.busy
  memoryEvents(M.DCACHE_WRITEBACK.id) := io.pmu.dcache.writeback
  memoryEvents(M.DCACHE_WALK_ACCESS.id) := io.pmu.dcacheWalkAccess
  memoryEvents(M.DCACHE_WALK_MISS.id) := io.pmu.dcacheWalkMiss
  memoryEvents(M.ITLB_MISS.id) := io.pmu.itlb.miss
  memoryEvents(M.ITLB_WALK_CYCLE.id) := io.pmu.itlb.walkCycle
  memoryEvents(M.DTLB_MISS.id) := io.pmu.dtlb.miss
  memoryEvents(M.DTLB_WALK_CYCLE.id) := io.pmu.dtlb.walkCycle
  csrfile.io.perfEvents := events

  // exclusive monitor
  excMon.io.flush         := control.io.flush
  excMon.io.check         <> resolve.io.excMon
  excMon.io.update.addr   := wb.io.excMon.addr
  excMon.io.update.set    := wb.io.excMon.set
  excMon.io.update.clear  := wb.io.excMon.clear
  excMon.io.update.clearAll := wb.io.excMon.clearAll

  // hazard resolver
  resolve.io.aluReg   <> alu.io.alu.reg
  resolve.io.memReg   <> mem.io.mem.reg
  resolve.io.memCsr   <> mem.io.mem.csr
  resolve.io.wbReg    <> wb.io.reg
  resolve.io.wbCsr    <> wb.io.csr
  resolve.io.wbExcMon <> wb.io.excMon

  // pipeline controller
  control.io.alu        := alu.io.stallReq
  control.io.mem        := mem.io.stallReq
  control.io.decFlush   := decoder.io.flushIf
  control.io.decTarget  := decoder.io.flushPc
  control.io.memFlush   := mem.io.flushReq
  control.io.memTarget  := mem.io.flushPc
  control.io.wbFlush    := csrfile.io.satpWritten
  control.io.wbTarget   := memwb.io.next.currentPc + 4.U
  control.io.load       := resolve.io.loadFlag
  control.io.csr        := resolve.io.csrFlag
  control.io.except     <> mem.io.except
  control.io.csrSepc    := csrfile.io.sepc
  control.io.csrMepc    := csrfile.io.mepc
  control.io.csrTvec    := csrfile.io.trapVec

  // TLB/cache control
  io.tlb.en           := csrfile.io.pageEn
  io.tlb.dataEn       := csrfile.io.dataPageEn
  io.tlb.dataSmode    := csrfile.io.dataMode === CSR_MODE_S
  io.tlb.mxr          := csrfile.io.mxr
  io.tlb.dataWrite    := mem.io.writeIntent
  io.tlb.flushInst    := mem.io.flushIt
  io.tlb.flushData    := mem.io.flushDt
  io.tlb.basePpn      := csrfile.io.basePpn
  io.tlb.sum          := csrfile.io.sum
  io.tlb.smode        := csrfile.io.mode(0)
  io.cache.flushInst  := mem.io.flushIc
  io.cache.flushData  := mem.io.flushDc
}
