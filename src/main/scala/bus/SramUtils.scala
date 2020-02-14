package bus

import chisel3._

import io.SramIO

class SramMux2(val addrWidth: Int, val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val sel2  = Input(Bool())
    val in    = Flipped(new SramIO(addrWidth, dataWidth))
    val out1  = new SramIO(addrWidth, dataWidth)
    val out2  = new SramIO(addrWidth, dataWidth)
  })

  io.in.valid := Mux(io.sel2, io.out2.valid, io.out1.valid)
  io.in.fault := Mux(io.sel2, io.out2.fault, io.out1.fault)
  io.in.rdata := Mux(io.sel2, io.out2.rdata, io.out1.rdata)

  io.out1.en    := !io.sel2
  io.out1.wen   := io.in.wen
  io.out1.addr  := io.in.addr
  io.out1.wdata := io.in.wdata

  io.out2.en    := io.sel2
  io.out2.wen   := io.in.wen
  io.out2.addr  := io.in.addr
  io.out2.wdata := io.in.wdata
}
