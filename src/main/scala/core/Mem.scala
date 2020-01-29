package core

import chisel3._
import chisel3.util._

import io._
import lsu._
import consts.Parameters._
import consts.LsuOp._
import lsu.LsuDecode._
import consts.ExceptType._
import consts.ExceptCause._
import consts.CSR._

class Mem extends Module {
  val io = IO(new Bundle {
    // from ALU
    val alu       = Input(new AluIO)
    // pipeline control
    val flush     = Input(Bool())
    val stallReq  = Output(Bool())
    // RAM interface
    val ram       = new SramIO(ADDR_WIDTH, DATA_WIDTH)
    // cache/TLB control
    val flushIc   = Output(Bool())
    val flushDc   = Output(Bool())
    val flushIt   = Output(Bool())
    val flushDt   = Output(Bool())
    // CSR status
    val csrHasInt = Input(Bool())
    val csrMode   = Input(UInt(CSR_MODE_WIDTH.W))
    // exception information
    val except    = Output(new ExceptInfoIO)
    // to next stage
    val mem       = Output(new MemIO)
  })

  // decode load store unit operation
  val ((en: Bool) :: (wen: Bool) :: width :: (signed: Bool) ::
       (setExcMon: Bool) :: (checkExcMon: Bool) :: amoOp ::
       (flushIc: Bool) :: (flushDc: Bool) :: (flushIt: Bool) ::
       (flushDt: Bool) :: Nil) = ListLookup(io.alu.lsuOp, DEFAULT, TABLE)

  // address of memory accessing
  val addr  = Cat(io.alu.reg.data(ADDR_WIDTH - 1, ADDR_ALIGN_WIDTH),
                  0.U(ADDR_ALIGN_WIDTH.W))
  val sel   = io.alu.reg.data(ADDR_ALIGN_WIDTH - 1, 0)

  // AMO execute unit
  val amo = Module(new AmoExecute)
  amo.io.op       := amoOp
  amo.io.flush    := io.flush
  amo.io.regOpr   := io.alu.lsuData
  amo.io.ramValid := io.ram.valid
  amo.io.ramRdata := io.ram.rdata

  // exclusive monitor
  val excMon  = Module(new ExclusiveMonitor)
  excMon.io.flush := io.flush
  excMon.io.set   := setExcMon
  excMon.io.clear := checkExcMon
  excMon.io.addr  := addr

  // write enable
  val selWord = "b1111".U((DATA_WIDTH / 8).W)
  val writeEn = MuxLookup(width, 0.U, Seq(
    LS_DATA_BYTE -> ("b1".U((DATA_WIDTH / 8).W) << sel),
    LS_DATA_HALF -> ("b11".U((DATA_WIDTH / 8).W) << sel),
    LS_DATA_WORD -> selWord,
  ))
  val scWen   = Mux(excMon.io.valid, selWord, 0.U)
  val amoWen  = Mux(amo.io.ramWen, selWord, 0.U)
  val ramWen  = Mux(wen, writeEn, Mux(checkExcMon, scWen, amoWen))

  // write data
  val wdata = Mux(wen || checkExcMon, io.alu.lsuData, amo.io.ramWdata)

  // stall request
  val stallReq  = Mux(amoOp =/= AMO_OP_NOP, !amo.io.ready, !io.ram.valid)

  // write back data
  val data = Mux(checkExcMon, Mux(excMon.io.valid, 0.U, 1.U),
             Mux(amoOp =/= AMO_OP_NOP, amo.io.regWdata, io.alu.reg.data))

  // exception related signals
  // signals about memory accessing
  val memAddr   = MuxLookup(width, false.B, Seq(
    LS_DATA_BYTE  -> false.B,
    LS_DATA_HALF  -> (sel(0) =/= 0.U),
    LS_DATA_WORD  -> (sel(1, 0) =/= 0.U),
  ))
  val memExcept = memAddr || io.ram.fault
  // signals about instruction fetching
  val instAddr  = io.alu.currentPc(ADDR_ALIGN_WIDTH - 1, 0) =/= 0.U
  val instPage  = io.alu.excType === EXC_IPAGE
  val instIllg  = io.alu.excType === EXC_ILLEG ||
                  (io.alu.excType === EXC_SPRIV &&
                   io.csrMode === CSR_MODE_U)
  // whether exception occurred
  val excMem    = io.alu.excType === EXC_LOAD ||
                  io.alu.excType === EXC_STAMO
  val hasExcept = instAddr || instIllg || (excMem && memExcept) ||
                  io.alu.excType =/= EXC_NONE
  // trap return instructions & interruptions
  val isSret    = io.alu.excType === EXC_SRET
  val isMret    = io.alu.excType === EXC_MRET
  val isInt     = io.csrHasInt
  // exception cause
  // NOTE: priority is important
  val cause     = MuxLookup(io.alu.excType, 0.U, Seq(
    EXC_ECALL -> Mux(io.csrMode === CSR_MODE_U, EXC_U_ECALL,
                 Mux(io.csrMode === CSR_MODE_S, EXC_S_ECALL, EXC_M_ECALL)),
    EXC_EBRK  -> EXC_BRK_POINT,
    EXC_LOAD  -> Mux(memAddr, EXC_LOAD_ADDR, EXC_LOAD_PAGE),
    EXC_STAMO -> Mux(memAddr, EXC_STAMO_ADDR, EXC_STAMO_PAGE),
  ))
  val excCause  = Mux(instPage, EXC_INST_PAGE,
                  Mux(instIllg, EXC_ILL_INST,
                  Mux(instAddr, EXC_INST_ADDR, cause)))
  // exception pc & value
  val excPc     = io.alu.currentPc
  val excValue  = Mux(io.alu.excType === EXC_ILLEG, io.alu.inst,
                  Mux(memExcept || instAddr || instPage,
                      io.alu.reg.data, 0.U))

  // pipeline control
  io.stallReq := stallReq

  // RAM control signals
  io.ram.en     := Mux(hasExcept, false.B, en)
  io.ram.wen    := ramWen
  io.ram.addr   := addr
  io.ram.wdata  := wdata

  // cache/TLB control signals
  io.flushIc  := flushIc
  io.flushDc  := flushDc
  io.flushIt  := Mux(hasExcept, false.B, flushIt)
  io.flushDt  := Mux(hasExcept, false.B, flushDt)

  // exception information
  io.except.hasExcept := hasExcept
  io.except.isSret    := isSret
  io.except.isMret    := isMret
  io.except.isInt     := isInt
  io.except.excCause  := excCause
  io.except.excPc     := excPc
  io.except.excValue  := excValue

  // output signals
  io.mem.reg.en     := io.alu.reg.en
  io.mem.reg.addr   := io.alu.reg.addr
  io.mem.reg.data   := data
  io.mem.reg.load   := io.alu.reg.load
  io.mem.memSigned  := signed
  io.mem.memSel     := sel
  io.mem.memWidth   := width
  io.mem.csr        <> io.alu.csr
  io.mem.currentPc  := io.alu.currentPc
}
