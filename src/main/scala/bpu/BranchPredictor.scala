package bpu

import chisel3._

import io.BranchInfoIO
import consts.Instructions.{JAL, JALR}
import consts.Parameters._

class BranchPredictor extends Module {
  val io = IO(new Bundle {
    // branch information (from decoder)
    val branchInfo  = Input(new BranchInfoIO)
    // predictor interface
    val lookupPc    = Input(UInt(ADDR_WIDTH.W))
    val lookupInst  = Input(UInt(INST_WIDTH.W))
    val lookupValid = Input(Bool()) // complete instruction, without fetch faults
    val advance     = Input(Bool()) // instruction accepted by the IF/ID FIFO
    val flush       = Input(Bool()) // backend flush, not a predicted redirect
    val predTaken   = Output(Bool())
    val predTarget  = Output(UInt(ADDR_WIDTH.W))
    val predIndex   = Output(UInt(GHR_WIDTH.W))
  })

  // instantiate necessary modules
  val ghr = Module(new GHR)
  val pht = Module(new PHT)
  val btb = Module(new BTB)
  val ras = Module(new ReturnAddressStack(RAS_SIZE))

  // wire GHR
  ghr.io.branch := io.branchInfo.branch
  ghr.io.taken  := io.branchInfo.taken

  // wire PHT
  val index = io.lookupPc(GHR_WIDTH + INST_ALIGN_WIDTH - 1,
                          INST_ALIGN_WIDTH) ^ ghr.io.ghr    // G-share
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

  // Predecode the actual instruction, so a return can use the RAS even on
  // its first encounter (BTB miss). Only the control-transfer encodings are
  // needed here; compressed instructions retain their two-byte link address.
  val inst = io.lookupInst
  val compressed = inst(1, 0) =/= 3.U
  val cJal = inst(1, 0) === 1.U && inst(15, 13) === 1.U
  val cJrOrJalr = inst(1, 0) === 2.U && inst(15, 13) === 4.U &&
                  inst(6, 2) === 0.U && inst(11, 7) =/= 0.U
  val cJalr = cJrOrJalr && inst(12)
  val jal = Mux(compressed, cJal, JAL === inst)
  val jalr = Mux(compressed, cJrOrJalr, JALR === inst)
  val rd = Mux(compressed, Mux(cJal || cJalr, 1.U, 0.U), inst(11, 7))
  val rs1 = Mux(compressed, inst(11, 7), inst(19, 15))
  val rdLink = rd === 1.U || rd === 5.U
  val rs1Link = rs1 === 1.U || rs1 === 5.U
  val push = (jal || jalr) && rdLink
  val pop = jalr && rs1Link && (!rdLink || rd =/= rs1)

  val updateRas = io.lookupValid && io.advance
  ras.io.flush := io.flush
  ras.io.push := updateRas && push
  ras.io.pop := updateRas && pop
  ras.io.pushAddr := io.lookupPc + Mux(compressed, 2.U, 4.U)
  val predictReturn = io.lookupValid && pop && ras.io.valid

  // wire output signals
  io.predTaken  := predictReturn ||
                   (btb.io.lookupBranch && (pht.io.taken || btb.io.lookupJump))
  io.predTarget := Mux(predictReturn, ras.io.top, btb.io.lookupTarget)
  io.predIndex  := index
}
