package util

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

  // reset stage IO
  val resetVal = Wire(sio)
  resetVal.default()

  // latch stage IO in every cycle
  val ff = RegInit(sio, resetVal)
  when (io.stallPrev && !io.stallNext) {
    ff := resetVal
  } .elsewhen(!io.stallPrev) {
    ff := io.prev
  }

  io.next := ff
}
