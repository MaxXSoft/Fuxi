package core

import chisel3._

import io._
import consts.Parameters._

class RegFile extends Module {
  val io = IO(new Bundle {
    val read1 = Flipped(new RegReadIO)
    val read2 = Flipped(new RegReadIO)
    val write = Flipped(new RegWriteIO)
  })

  val init = Seq.fill(REG_COUNT) { 0.U(DATA_WIDTH.W) }
  val regfile = RegInit(VecInit(init))

  // read channels
  io.read1.data := Mux(io.read1.en, regfile(io.read1.addr), 0.U)
  io.read2.data := Mux(io.read2.en, regfile(io.read2.addr), 0.U)

  // write channel
  when (io.write.en && io.write.addr =/= 0.U) {
    regfile(io.write.addr) := io.write.data
  }
}
