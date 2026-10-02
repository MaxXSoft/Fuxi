package bpu

import chisel3._
import chisel3.util._

import consts.Parameters.{ADDR_WIDTH, RAS_SIZE}
import io.RasPerformanceIO

// A bounded speculative stack. Overflow discards the oldest return address;
// underflow supplies no prediction. Backend flushes discard all history.
class ReturnAddressStack(depth: Int = RAS_SIZE) extends Module {
  require(depth > 0)

  val io = IO(new Bundle {
    val flush = Input(Bool())
    val push = Input(Bool())
    val pop = Input(Bool())
    val pushAddr = Input(UInt(ADDR_WIDTH.W))
    val valid = Output(Bool())
    val top = Output(UInt(ADDR_WIDTH.W))
    val pmu = Output(new RasPerformanceIO)
  })

  val entries = Reg(Vec(depth, UInt(ADDR_WIDTH.W)))
  val next = RegInit(0.U(math.max(1, log2Ceil(depth)).W))
  val count = RegInit(0.U(log2Ceil(depth + 1).W))
  val previous = Mux(next === 0.U, (depth - 1).U, next - 1.U)
  val nonEmpty = count =/= 0.U
  def entry(index: UInt): UInt = if (depth == 1) entries(0) else entries(index)

  io.valid := nonEmpty
  io.top := Mux(nonEmpty, entry(previous), 0.U)
  io.pmu.overflow := !io.flush && io.push && !io.pop && count === depth.U
  io.pmu.emptyPop := !io.flush && io.pop && !nonEmpty
  io.pmu.clearNonempty := io.flush && nonEmpty

  when (io.flush) {
    next := 0.U
    count := 0.U
  } .elsewhen (io.push) {
    when (io.pop && nonEmpty) {
      // Pop-then-push replaces the old top, without discarding the bottom
      // when full. Prediction still reads the old top before this edge.
      entry(previous) := io.pushAddr
    } .otherwise {
      entry(next) := io.pushAddr
      next := Mux(next === (depth - 1).U, 0.U, next + 1.U)
      when (count =/= depth.U) { count := count + 1.U }
    }
  } .elsewhen (io.pop && nonEmpty) {
    next := previous
    count := count - 1.U
  }
}
