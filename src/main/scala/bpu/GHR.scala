package bpu

import chisel3._
import chisel3.util.Cat

import consts.Parameters.GHR_WIDTH

// global history register
class GHR extends Module {
  val io = IO(new Bundle {
    // branch information
    val branch  = Input(Bool())
    val taken   = Input(Bool())
    // value of GHR
    val ghr     = Output(UInt(GHR_WIDTH.W))
  })

  val ghr = Reg(UInt(GHR_WIDTH.W))

  when (io.branch) {
    ghr := Cat(ghr(GHR_WIDTH - 2, 0), io.taken)
  }

  io.ghr := ghr
}
