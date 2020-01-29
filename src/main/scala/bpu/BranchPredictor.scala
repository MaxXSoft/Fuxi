package bpu

import chisel3._

import io.BranchInfoIO
import consts.Parameters._

class BranchPredictor extends Module {
  val io = IO(new Bundle {
    // branch information (from decoder)
    val branchInfo  = Input(new BranchInfoIO)
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
  ghr.io.branch := io.branchInfo.branch
  ghr.io.taken  := io.branchInfo.taken

  // wire PHT
  val index = io.lookupPc(GHR_WIDTH + ADDR_ALIGN_WIDTH - 1,
                          ADDR_ALIGN_WIDTH) ^ ghr.io.ghr    // G-share
  pht.io.lastBranch := io.branchInfo.branch
  pht.io.lastTaken  := io.branchInfo.taken
  pht.io.lastIndex  := io.branchInfo.index
  pht.io.index      := index

  // wire BTB
  btb.io.branch   := io.branchInfo.branch
  btb.io.jump     := io.branchInfo.jump
  btb.io.pc       := io.branchInfo.pc
  btb.io.target   := io.branchInfo.target
  btb.io.lookupPc := io.lookupPc

  // wire output signals
  io.predTaken  := btb.io.lookupBranch &&
                   (pht.io.taken || btb.io.lookupJump)
  io.predTarget := btb.io.lookupTarget
  io.predIndex  := index
}
