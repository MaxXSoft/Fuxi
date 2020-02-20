package utils

import chisel3._
import chisel3.util._

// block memory (BRAM)
class BlockMem(val size: Int, val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val en    = Input(Bool())
    val wen   = Input(UInt((dataWidth / 8).W))
    val addr  = Input(UInt(log2Ceil(size).W))
    val wdata = Input(UInt(dataWidth.W))
    val rdata = Output(UInt(dataWidth.W))
  })

  def toBytes(data: UInt) = VecInit(0 until data.getWidth / 8 map {
    i => data((i + 1) * 8 - 1, i * 8)
  })

  val mem = SyncReadMem(size, Vec(dataWidth / 8, UInt(8.W)))

  io.rdata := DontCare
  when (io.en) {
    when (io.wen =/= 0.U) {
      mem.write(io.addr, toBytes(io.wdata), io.wen.asBools)
    } .otherwise {
      io.rdata := Cat(mem.read(io.addr).reverse)
    }
  }
}
