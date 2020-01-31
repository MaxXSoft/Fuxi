package utils

import chisel3._

import io._
import consts.Parameters._

class PipelineController extends Module {
  val io = IO(new Bundle {
    // stall request from pipeline stages
    val fetch   = Input(Bool())
    val alu     = Input(Bool())
    val mem     = Input(Bool())
    // hazard flags
    val load    = Input(Bool())
    val csr     = Input(Bool())
    // exception information
    val except  = Input(new ExceptInfoIO)
    // CSR status
    val csrSepc = Input(UInt(ADDR_WIDTH.W))
    val csrMepc = Input(UInt(ADDR_WIDTH.W))
    val csrTvec = Input(UInt(ADDR_WIDTH.W))
    // stall signals to each mig-stages
    val stallIf = Output(Bool())
    val stallId = Output(Bool())
    val stallEx = Output(Bool())
    val stallMm = Output(Bool())
    val stallWb = Output(Bool())
    // flush signals
    val flush   = Output(Bool())
    val excPc   = Output(UInt(ADDR_WIDTH.W))
  })

  // stall signals (If -> Wb)
  val stall = Mux(io.mem,           "b11110".U(5.W),
              Mux(io.csr || io.alu, "b11100".U(5.W),
              Mux(io.load,          "b11000".U(5.W),
              Mux(io.fetch,         "b10000".U(5.W), 0.U))))

  // exception PC
  val excPc = Mux(io.except.isSret, io.csrSepc,
              Mux(io.except.isMret, io.csrMepc, io.csrTvec))

  // stall signals
  io.stallIf  := stall(4)
  io.stallId  := stall(3)
  io.stallEx  := stall(2)
  io.stallMm  := stall(1)
  io.stallWb  := stall(0)

  // flush signals
  io.flush  := io.except.hasExcept
  io.excPc  := excPc
}
