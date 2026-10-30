package utils

import chisel3._

import io._
import consts.Parameters._

class PipelineController extends Module {
  val io = IO(new Bundle {
    // stall request from pipeline stages
    val alu       = Input(Bool())
    val mem       = Input(Bool())
    // flush request from pipeline stages
    val decFlush  = Input(Bool())
    val decTarget = Input(UInt(ADDR_WIDTH.W))
    val memFlush  = Input(Bool())
    val memTarget = Input(UInt(ADDR_WIDTH.W))
    val wbFlush   = Input(Bool())
    val wbTarget  = Input(UInt(ADDR_WIDTH.W))
    // hazard flags
    val load      = Input(Bool())
    val csr       = Input(Bool())
    // exception information
    val except    = Input(new ExceptInfoIO)
    // CSR status
    val csrSepc   = Input(UInt(ADDR_WIDTH.W))
    val csrMepc   = Input(UInt(ADDR_WIDTH.W))
    val csrTvec   = Input(UInt(ADDR_WIDTH.W))
    // stall signals to the decode/execute pipeline; fetch has its own queue
    val stallId   = Output(Bool())
    val stallEx   = Output(Bool())
    val stallMm   = Output(Bool())
    val stallWb   = Output(Bool())
    // flush signals
    val flush      = Output(Bool())
    val flushIf    = Output(Bool())
    val flushPc    = Output(UInt(ADDR_WIDTH.W))
    val flushMemWb = Output(Bool())
  })

  // stall signals (Id -> Wb)
  val stall = Mux(io.mem,           "b1110".U(4.W),
              Mux(io.csr || io.alu, "b1100".U(4.W),
              Mux(io.load,          "b1000".U(4.W), 0.U)))

  // final exception PC
  val excPc   = Mux(io.except.isSret, io.csrSepc,
                Mux(io.except.isMret, io.csrMepc, io.csrTvec))

  // flush signals
  val excFlush  = io.except.hasTrap
  // A committing SATP write redirects from WB. Mem holds younger accesses,
  // fences and traps while the CSR write is busy, so they cannot compete.
  val flushAll  = excFlush || io.wbFlush || io.memFlush
  val flushIf   = flushAll || io.decFlush
  val flushPc   = Mux(excFlush, excPc,
                  Mux(io.wbFlush, io.wbTarget,
                  Mux(io.memFlush, io.memTarget, io.decTarget)))

  // Fences and legal xRET instructions redirect younger stages but still
  // retire. Synchronous faults and interrupted instructions are discarded.
  val serializingDone = (io.memFlush && !excFlush) ||
    (excFlush && (io.except.isSret || io.except.isMret))

  // stall signals
  io.stallId  := stall(3)
  io.stallEx  := stall(2)
  io.stallMm  := stall(1)
  io.stallWb  := stall(0)

  // flush signals
  io.flush      := flushAll
  io.flushIf    := flushIf
  io.flushPc    := flushPc
  io.flushMemWb := flushAll && !serializingDone
}
