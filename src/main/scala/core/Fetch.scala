package core

import chisel3._

import io._
import consts.Parameters._
import bpu.BranchPredictor

class Fetch extends Module {
  val io = IO(new Bundle {
    // pipeline control signals
    val flush     = Input(Bool())
    val stall     = Input(Bool())
    val stallReq  = Output(Bool())
    val flushPc   = Input(UInt(ADDR_WIDTH.W))
    // ROM interface
    val rom       = new SramIO(ADDR_WIDTH, INST_WIDTH)
    // branch information (from decoder)
    val branch    = Input(new BranchInfoIO)
    // to next stage
    val fetch     = Output(new FetchIO)
  })

  // program counter
  val pc = RegInit(RESET_PC)

  // branch predictor
  val bpu = Module(new BranchPredictor)
  bpu.io.branchInfo <> io.branch
  bpu.io.lookupPc   := pc

  // update PC
  val nextPc = Mux(io.flush, io.flushPc,
               Mux(io.stall, pc,
               Mux(bpu.io.predTaken, bpu.io.predTarget,
                   pc + (INST_WIDTH / 8).U)))
  pc := nextPc

  // pipeline control signals
  io.stallReq := !io.rom.valid

  // ROM control signals
  io.rom.en     := true.B
  io.rom.wen    := 0.U
  io.rom.addr   := pc
  io.rom.wdata  := 0.U

  // output signals
  io.fetch.valid      := io.rom.valid
  io.fetch.pc         := pc
  io.fetch.taken      := bpu.io.predTaken
  io.fetch.target     := bpu.io.predTarget
  io.fetch.predIndex  := bpu.io.predIndex
  io.fetch.pageFault  := io.rom.fault
}
