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
import consts.CSR.CSR_SATP
import consts.CsrOp.{CSR_NOP, CSR_R}

class Core(val fetchQueueDepth: Int, val hpmCount: Int = 8) extends Module {
  val io = IO(new Bundle {
    // interrupt request
    val irq   = new InterruptIO
    val rtcTime = Input(UInt(64.W))
    // TLB/cache control
    val tlb   = new TlbControlIO(PPN_WIDTH)
    val cache = new CacheControlIO
    // ROM interface (I-cache)
    val rom   = new SramIO(ADDR_WIDTH, INST_WIDTH)
    // RAM interface (D-cache)
    val ram   = new SramIO(ADDR_WIDTH, DATA_WIDTH)
    // for trace generating
    val debug = new DebugIO
    val busPerf = Input(new BusPerformanceIO)
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

  // SATP takes effect at writeback. Discard instructions fetched under the
  // old address space before any of them can retire (for example Linux's
  // Bare-to-Sv32 trampoline). Mem already holds younger accesses/traps while
  // this CSR write commits. TLB invalidation remains the job of SFENCE.VMA.
  val satpWrite = wb.io.csr.retired && wb.io.csr.addr === CSR_SATP &&
    wb.io.csr.op =/= CSR_NOP && wb.io.csr.op =/= CSR_R

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
  // Fences and legal xRET instructions redirect younger stages but still
  // retire. Synchronous faults and interrupted instructions are discarded.
  val serializingDone = (mem.io.flushReq && !mem.io.except.hasTrap) ||
    (mem.io.except.hasTrap && (mem.io.except.isSret || mem.io.except.isMret))
  memwb.io.flush      := control.io.flush && !serializingDone
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
  val events = WireInit(VecInit(Seq.fill(64)(false.B)))
  val retired = wb.io.csr.retired
  val p = memwb.io.next.perf
  events(BRANCH) := retired && p.branch
  events(BRANCH_MISS) := retired && p.branch && p.miss
  events(BRANCH_DIRECTION_MISS) := retired && p.branch && p.directionMiss
  events(BRANCH_TARGET_MISS) := retired && p.branch && p.targetMiss
  events(JAL) := retired && p.jal
  events(JAL_MISS) := retired && p.jal && p.miss
  events(JALR) := retired && p.jalr
  events(JALR_MISS) := retired && p.jalr && p.miss
  events(RAS_CANDIDATE) := retired && p.jalr && p.rasCandidate
  events(RAS_USED) := retired && p.jalr && p.rasUsed
  events(RAS_MISS) := retired && p.jalr && p.rasUsed && p.miss
  events(RAS_FALLBACK_MISS) := retired && p.jalr && p.rasCandidate && !p.rasUsed && p.miss
  events(OTHER_JALR) := retired && p.jalr && !p.rasCandidate
  events(OTHER_JALR_MISS) := retired && p.jalr && !p.rasCandidate && p.miss
  events(COMPRESSED) := retired && p.compressed
  events(LOAD) := retired && p.load
  events(STORE) := retired && p.store
  events(FRONTEND_EMPTY) := !control.io.flushIf && !control.io.stallId && !ifid.io.deq.valid
  events(LOAD_USE_STALL) := !control.io.flush && resolve.io.loadFlag
  events(MDU_STALL) := !control.io.flush && alu.io.stallReq
  events(MEM_STALL) := !control.io.flush && mem.io.stallReq
  events(CSR_STALL) := !control.io.flush && resolve.io.csrFlag
  events(FETCH_QUEUE_FULL) := !control.io.flushIf && fetch.io.fetch.valid && !ifid.io.enq.ready
  events(DECODE_REDIRECT) := !control.io.flush && decoder.io.flushIf
  events(CONTEXT_FLUSH) := control.io.flush
  events(RAS_OVERFLOW) := fetch.io.rasOverflow
  events(RAS_EMPTY_POP) := fetch.io.rasEmptyPop
  events(RAS_CLEAR_MISPREDICT) := fetch.io.rasClearNonempty && !control.io.flush
  events(RAS_CLEAR_CONTEXT) := fetch.io.rasClearNonempty && control.io.flush
  events(ICACHE_ACCESS) := io.busPerf.icacheAccess
  events(ICACHE_MISS) := io.busPerf.icacheMiss
  events(ICACHE_BUSY) := io.busPerf.icacheBusy
  events(ICACHE_WALK_ACCESS) := io.busPerf.icacheWalkAccess
  events(ICACHE_WALK_MISS) := io.busPerf.icacheWalkMiss
  events(DCACHE_ACCESS) := io.busPerf.dcacheAccess
  events(DCACHE_MISS) := io.busPerf.dcacheMiss
  events(DCACHE_BUSY) := io.busPerf.dcacheBusy
  events(DCACHE_WRITEBACK) := io.busPerf.dcacheWriteback
  events(DCACHE_WALK_ACCESS) := io.busPerf.dcacheWalkAccess
  events(DCACHE_WALK_MISS) := io.busPerf.dcacheWalkMiss
  events(ITLB_MISS) := io.busPerf.itlbMiss
  events(ITLB_WALK_CYCLE) := io.busPerf.itlbWalkCycle
  events(DTLB_MISS) := io.busPerf.dtlbMiss
  events(DTLB_WALK_CYCLE) := io.busPerf.dtlbWalkCycle
  csrfile.io.perfEvents := events.asUInt

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
  control.io.memFlush   := mem.io.flushReq || satpWrite
  control.io.memTarget  := Mux(satpWrite, memwb.io.next.currentPc + 4.U, mem.io.flushPc)
  control.io.load       := resolve.io.loadFlag
  control.io.csr        := resolve.io.csrFlag
  control.io.except     <> mem.io.except
  control.io.csrSepc    := csrfile.io.sepc
  control.io.csrMepc    := csrfile.io.mepc
  control.io.csrTvec    := csrfile.io.trapVec

  // TLB/cache control
  io.tlb.en           := csrfile.io.pageEn
  io.tlb.dataEn       := csrfile.io.dataPageEn
  io.tlb.dataSmode    := csrfile.io.dataMode === consts.CSR.CSR_MODE_S
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
