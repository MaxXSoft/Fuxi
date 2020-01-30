package sim

import chisel3._
import chisel3.util.Cat

import io._
import consts.Parameters._

// for simulation only
class RAM extends Module {
  val io = IO(Flipped(new SramIO(ADDR_WIDTH, DATA_WIDTH)))

  val addr = io.addr(ADDR_WIDTH - 1, ADDR_ALIGN_WIDTH)
  val data = Reg(Vec(DATA_WIDTH / 8, UInt(8.W)))

  for (i <- 0 until DATA_WIDTH / 8) {
    val ram = Mem(256, UInt(8.W))
    when (io.en) {
      when (io.wen(i)) {
        ram(addr) := io.wdata((i + 1) * 8 - 1, i * 8)
      } .otherwise {
        data(i) := ram(addr)
      }
    }
  }

  io.valid := true.B
  io.fault := false.B
  io.rdata := Cat(data.reverse)
}
