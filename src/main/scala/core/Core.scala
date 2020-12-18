package core

import chisel3._

import io._
import consts.Parameters._
import utils._
import csr.CsrFile
import lsu.ExclusiveMonitor
import consts.Paging.PPN_WIDTH

class Core extends Module {
  val io = IO(new Bundle {
    // interrupt request
    val irq   = new InterruptIO
    // TLB/cache control
    val tlb   = new TlbControlIO(PPN_WIDTH)
    val cache = new CacheControlIO
    // ROM interface (I-cache)
    val rom   = new SramIO(ADDR_WIDTH, INST_WIDTH)
    // RAM interface (D-cache)
    val ram   = new SramIO(ADDR_WIDTH, DATA_WIDTH)
    // for trace generating
    val debug = new DebugIO
  })

  // all stages
  val fetch   = Module(new Fetch)
  val ifid    = Module(new MidStage(new FetchIO))
  val decoder = Module(new Decoder)
  val idex    = Module(new MidStage(new DecoderIO))
  val alu     = Module(new ALU)
  val exmem   = Module(new MidStage(new AluIO))
  val mem     = Module(new Mem)
  val memwb   = Module(new MidStage(new MemIO))
  val wb      = Module(new WriteBack)

  // register file
  val regfile = Module(new RegFile)

  // CSR file
  var csrfile = Module(new CsrFile)

  // exclusive monitor
  val excMon  = Module(new ExclusiveMonitor)

  // hazard resolve & pipeline control
  val resolve = Module(new HazardResolver)
  val control = Module(new PipelineController)

  // fetch stage
  fetch.io.flush    := control.io.flushIf
  fetch.io.stall    := control.io.stallIf
  fetch.io.flushPc  := control.io.flushPc
  fetch.io.rom      <> io.rom
  fetch.io.branch   <> decoder.io.branch
  ifid.io.flush     := control.io.flushIf
  ifid.io.stallPrev := control.io.stallIf
  ifid.io.stallNext := control.io.stallId
  ifid.io.prev      <> fetch.io.fetch

  // decoder stage
  decoder.io.fetch    <> ifid.io.next
  decoder.io.inst     := io.rom.rdata
  decoder.io.stallId  := control.io.stallId
  decoder.io.read1    <> resolve.io.regRead1
  decoder.io.read2    <> resolve.io.regRead2
  idex.io.flush       := control.io.flush
  idex.io.stallPrev   := control.io.stallId
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
  mem.io.excMon       <> resolve.io.check
  memwb.io.flush      := control.io.flush
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
  csrfile.io.except         <> mem.io.except

  // exclusive monitor
  excMon.io.flush         := control.io.flush
  excMon.io.check         <> resolve.io.excMon
  excMon.io.update.addr   := wb.io.excMon.addr
  excMon.io.update.set    := wb.io.excMon.set
  excMon.io.update.clear  := wb.io.excMon.clear

  // hazard resolver
  resolve.io.aluReg   <> alu.io.alu.reg
  resolve.io.memReg   <> mem.io.mem.reg
  resolve.io.memCsr   <> mem.io.mem.csr
  resolve.io.wbReg    <> wb.io.reg
  resolve.io.wbCsr    <> wb.io.csr
  resolve.io.wbExcMon <> wb.io.excMon

  // pipeline controller
  control.io.fetch      := fetch.io.stallReq
  control.io.alu        := alu.io.stallReq
  control.io.mem        := mem.io.stallReq
  control.io.decFlush   := decoder.io.flushIf
  control.io.decTarget  := decoder.io.flushPc
  control.io.memFlush   := mem.io.flushReq
  control.io.memTarget  := mem.io.flushPc
  control.io.load       := resolve.io.loadFlag
  control.io.csr        := resolve.io.csrFlag
  control.io.except     <> mem.io.except
  control.io.csrSepc    := csrfile.io.sepc
  control.io.csrMepc    := csrfile.io.mepc
  control.io.csrTvec    := csrfile.io.trapVec

  // TLB/cache control
  io.tlb.en           := csrfile.io.pageEn
  io.tlb.flushInst    := mem.io.flushIt
  io.tlb.flushData    := mem.io.flushDt
  io.tlb.basePpn      := csrfile.io.basePpn
  io.tlb.sum          := csrfile.io.sum
  io.tlb.smode        := csrfile.io.mode(0)
  io.cache.flushInst  := mem.io.flushIc
  io.cache.flushData  := mem.io.flushDc
}
