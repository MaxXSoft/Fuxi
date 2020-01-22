package bpu

import chisel3._
import chisel3.util.Cat

import consts.Constants._

// single line in BTB
class BTBLine extends Bundle {
  val valid   = Bool()
  val jump    = Bool()
  val pc      = UInt(BTB_PC_WIDTH.W)
  val target  = UInt(BTB_TARGET_WIDTH.W)
}

// branch target buffer
class BTB extends Module {
  val io = IO(new Bundle {
    // branch information (from decoder)
    val branch        = Input(Bool())
    val jump          = Input(Bool())
    val pc            = Input(UInt(ADDR_WIDTH.W))
    val target        = Input(UInt(ADDR_WIDTH.W))
    // BTB lookup interface
    val lookupPc      = Input(UInt(ADDR_WIDTH.W))
    val lookupBranch  = Output(Bool())
    val lookupJump    = Output(Bool())
    val lookupTarget  = Output(UInt(ADDR_WIDTH.W))
  })

  // definitions of BTB lines
  val init  = Seq.fill(BTB_SIZE) { 0.U.asTypeOf(new BTBLine) }
  val lines = RegInit(VecInit(init))
  // val lines = Mem(BTB_SIZE, new BTBLine)

  // branch info for BTB lines
  val index   = io.pc(BTB_INDEX_WIDTH + ADDR_ALIGN_WIDTH - 1,
                      ADDR_ALIGN_WIDTH)
  val linePc  = io.pc(ADDR_WIDTH - 1, BTB_INDEX_WIDTH + ADDR_ALIGN_WIDTH)

  // write to BTB lines
  when (io.branch) {
    lines(index).valid  := true.B
    lines(index).jump   := io.jump
    lines(index).pc     := linePc
    lines(index).target := io.target(ADDR_WIDTH - 1, ADDR_ALIGN_WIDTH)
  }

  // signals about BTB lookup
  val lookupIndex = io.lookupPc(BTB_INDEX_WIDTH + ADDR_ALIGN_WIDTH - 1,
                                ADDR_ALIGN_WIDTH)
  val lookupPcSel = io.lookupPc(ADDR_WIDTH - 1,
                                BTB_INDEX_WIDTH + ADDR_ALIGN_WIDTH)
  val btbHit      = lines(lookupIndex).valid &&
                    lines(lookupIndex).pc === lookupPcSel

  // BTB lookup
  io.lookupBranch := btbHit
  io.lookupJump   := Mux(btbHit, lines(lookupIndex).jump, false.B)
  io.lookupTarget := Cat(Mux(btbHit, lines(lookupIndex).target, 0.U),
                         0.U(ADDR_ALIGN_WIDTH.W))
}
