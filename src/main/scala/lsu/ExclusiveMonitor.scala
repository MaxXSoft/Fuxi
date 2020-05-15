package lsu

import chisel3._

import io._
import consts.Parameters._

// for LR & SC instructions
class ExclusiveMonitor extends Module {
  val io = IO(new Bundle {
    // pipeline control
    val flush   = Input(Bool())
    // check channel
    val check   = Flipped(new ExcMonCheckIO)
    // update channel
    val update  = Flipped(new ExcMonUpdateIO)
  })

  val flag  = RegInit(false.B)
  val addr  = RegInit(0.U(ADDR_WIDTH.W))

  when (io.flush || (io.update.clear && io.update.addr === addr)) {
    flag  := false.B
    addr  := 0.U
  } .elsewhen (io.update.set) {
    flag  := true.B
    addr  := io.update.addr
  }

  io.check.valid := flag && addr === io.check.addr
}
