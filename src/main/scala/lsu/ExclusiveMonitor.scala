package lsu

import chisel3._

import consts.Constants._

// for LR & SC instructions
class ExclusiveMonitor extends Module {
  val io = IO(new Bundle {
    // write channel
    val set       = Input(Bool())
    val clear     = Input(Bool())
    val addrSet   = Input(UInt(ADDR_WIDTH.W))
    // check channel
    val addrCheck = Input(UInt(ADDR_WIDTH.W))
    val valid     = Output(Bool())
  })

  val flag  = RegInit(false.B)
  val addr  = RegInit(0.U(ADDR_WIDTH.W))

  when (io.clear) {
    flag  := false.B
    addr  := 0.U
  } .elsewhen (io.set) {
    flag  := true.B
    addr  := io.addrSet
  }

  io.valid := flag && addr === io.addrCheck
}
