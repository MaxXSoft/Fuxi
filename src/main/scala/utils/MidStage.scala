package utils

import chisel3._

import io._

class MidStage[T <: StageIO[T]](sio: T) extends Module {
  val io = IO(new Bundle {
    // stall control
    val stallPrev = Input(Bool())
    val stallNext = Input(Bool())
    // IO of previous/next stage
    val prev      = Input(sio)
    val next      = Output(sio)
  })

  // latch stage IO in every cycle
  val ff = RegInit(sio, sio.default())
  when (io.stallPrev && !io.stallNext) {
    ff := sio.default()
  } .elsewhen(!io.stallPrev) {
    ff := io.prev
  }

  io.next := ff
}
