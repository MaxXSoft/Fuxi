package bpu

import chisel3._

import consts.Constants._

class BranchPredictor extends Module {
  val io = IO(new Bundle {
    // branch information (from decoder)
    val branch      = Input(Bool())               // last inst is a b/j
    val jump        = Input(Bool())               // is 'jal' or 'jalr'
    val taken       = Input(Bool())               // is last branch taken
    val index       = Input(UInt(GHR_WIDTH.W))    // last index of PHT
    val pc          = Input(UInt(ADDR_WIDTH.W))   // last instruction PC
    val target      = Input(UInt(ADDR_WIDTH.W))   // last branch target
    // predictor interface
    val lookupPc    = Input(UInt(ADDR_WIDTH.W))
    val predTaken   = Output(Bool())
    val predTarget  = Output(UInt(ADDR_WIDTH.W))
    val predIndex   = Output(UInt(GHR_WIDTH.W))
  })

  // instantiate necessary modules
  val ghr = Module(new GHR)
  val pht = Module(new PHT)
  val btb = Module(new BTB)

  // wire GHR
  ghr.io.branch := io.branch
  ghr.io.taken  := io.taken

  // wire PHT
  val index = io.lookupPc(GHR_WIDTH + ADDR_ALIGN_WIDTH - 1,
                          ADDR_ALIGN_WIDTH) ^ ghr.io.ghr    // G-share
  pht.io.lastBranch := io.branch
  pht.io.lastTaken  := io.taken
  pht.io.lastIndex  := io.index
  pht.io.index      := index

  // wire BTB
  btb.io.branch   := io.branch
  btb.io.jump     := io.jump
  btb.io.pc       := io.pc
  btb.io.target   := io.target
  btb.io.lookupPc := io.lookupPc

  // wire output signals
  io.predTaken  := btb.io.lookupBranch &&
                   (pht.io.taken || btb.io.lookupJump)
  io.predTarget := btb.io.lookupTarget
  io.predIndex  := index
}
