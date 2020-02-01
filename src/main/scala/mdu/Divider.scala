package mdu

import chisel3._
import chisel3.util._

// N-bit divider, calculate 2 bits per cycle
class Divider(val oprWidth: Int) extends Module {
  val io = IO(new Bundle {
    // control signals
    val en        = Input(Bool())
    val flush     = Input(Bool())
    val divZero   = Output(Bool())
    val done      = Output(Bool())
    // operands & results
    val divident  = Input(UInt(oprWidth.W))
    val divisor   = Input(UInt(oprWidth.W))
    val quotient  = Output(UInt(oprWidth.W))
    val remainder = Output(UInt(oprWidth.W))
  })

  // some constant parameters
  val resultWidth = oprWidth * 2 + 1
  val cycleCount  = (oprWidth / 2f).ceil.toInt

  // state of finite state machine
  val sIdle :: sRunning :: sEnd :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // some data registers
  // result of division (remainder, 1'b0, quotient)
  val result        = RegInit(0.U(resultWidth.W))
  // cycle counter
  val counter       = RegInit(0.U(log2Ceil(cycleCount).W))
  // flag of divided by zero
  val isDiv0        = RegInit(false.B)
  // last divident & divisor
  val lastDivident  = RegInit(0.U(oprWidth.W))
  val lastDivisor   = RegInit(0.U(oprWidth.W))

  // divisor * 1
  val divisor     = RegInit(0.U(resultWidth.W))
  // divisor * 0.5
  val minDivisor  = divisor >> 1
  // divisor * 1.5
  val maxDivisor  = divisor + minDivisor
  // start flag
  val startFlag   = lastDivident =/= io.divident ||
                    lastDivisor =/= io.divisor

  // finite state machine
  when (io.flush) {
    state   := sIdle
    result  := 0.U
    isDiv0  := false.B
  } .otherwise {
    switch (state) {
      is (sIdle) {
        when (io.en) {
          when (startFlag) {
            // start new calculation
            lastDivident  := io.divident
            lastDivisor   := io.divisor
            // switch to next state
            when (io.divisor === 0.U) {
              state   := sEnd
              isDiv0  := true.B
            } .otherwise {
              state   := sRunning
              result  := Cat(0.U(oprWidth.W), io.divident, 0.U(1.W))
              divisor := Cat(0.U(1.W), io.divisor, 0.U(oprWidth.W))
              counter := 0.U
              isDiv0  := false.B
            }
          } .otherwise {
            // reuse previous results
            state := sEnd
          }
        }
      }
      is (sRunning) {
        // generate result
        when (result >= maxDivisor) {
          result := ((result - maxDivisor) << 2) | "b11".U
        } .elsewhen (result < maxDivisor && result >= divisor) {
          result := ((result - divisor) << 2) | "b10".U
        } .elsewhen (result < divisor && result >= minDivisor) {
          result := ((result - minDivisor) << 2) | "b01".U
        } .otherwise {
          result := result << 2
        }
        // increase/check counter
        counter := counter + 1.U
        when (counter === (cycleCount - 1).U) { state := sEnd }
      }
      is (sEnd) {
        state := sIdle
      }
    }
  }

  // generate output signals
  io.divZero    := isDiv0
  io.done       := state === sEnd
  io.quotient   := result(oprWidth - 1, 0)
  io.remainder  := result(resultWidth - 1, oprWidth + 1)
}
