package mdu

import chisel3._
import chisel3.util._

// N-bit pipelined multiplier
class Multiplier(val oprWidth: Int, val latency: Int = 0) extends Module {
  val io = IO(new Bundle {
    // control signals
    val en    = Input(Bool())
    val flush = Input(Bool())
    val done  = Output(Bool())
    // operands & results
    val opr1  = Input(UInt(oprWidth.W))
    val opr2  = Input(UInt(oprWidth.W))
    val lo    = Output(UInt(oprWidth.W))
    val hi    = Output(UInt(oprWidth.W))
  })

  require(latency >= 0,
          "Multiplier latency must be greater than or equal to zero!")

  // product
  val result  = io.opr1 * io.opr2

  def generatePipe(en: Bool, data: UInt, latency: Int): (Bool, UInt) = {
    if (latency == 0) {
      (en, data)
    }
    else {
      val done  = RegNext(Mux(io.flush, false.B, en), false.B)
      val bits  = RegEnable(data, en)
      generatePipe(done, bits, latency - 1)
    }
  }

  // generate output
  val (en, data)  = generatePipe(io.en, result, latency)
  io.done        := en
  io.lo          := data(oprWidth - 1, 0)
  io.hi          := data(oprWidth * 2 - 1, oprWidth)
}
