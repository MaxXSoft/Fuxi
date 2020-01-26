package mdu

import chisel3._
import chisel3.util._

// N-bit multiplier
class Multiplier(val oprWidth: Int, val latency: Int = 0) extends Module {
  val io = IO(new Bundle {
    // control signals
    val en    = Input(Bool())
    val done  = Output(Bool())
    // operands & results
    val opr1  = Input(UInt(oprWidth.W))
    val opr2  = Input(UInt(oprWidth.W))
    val lo    = Output(UInt(oprWidth.W))
    val hi    = Output(UInt(oprWidth.W))
  })

  // product
  val result  = io.opr1 * io.opr2

  // generate output
  val piped = Pipe(io.en, result, latency)
  io.done  := piped.valid
  io.lo    := piped.bits(oprWidth - 1, 0)
  io.hi    := piped.bits(oprWidth * 2 - 1, oprWidth)
}
