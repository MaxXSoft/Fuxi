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

  // delay for read data
  val rdata_sel = RegNext(io.sel2)

  io.in.valid   := Mux(io.sel2, io.out2.valid, io.out1.valid)
  io.in.fault   := Mux(io.sel2, io.out2.fault, io.out1.fault)
  io.in.rdata   := Mux(rdata_sel, io.out2.rdata, io.out1.rdata)

  io.out1.en    := io.in.en && !io.sel2
  io.out1.wen   := io.in.wen
  io.out1.addr  := io.in.addr
  io.out1.wdata := io.in.wdata

  io.out2.en    := io.in.en && io.sel2
  io.out2.wen   := io.in.wen
  io.out2.addr  := io.in.addr
  io.out2.wdata := io.in.wdata
}

class SramDemux2(val addrWidth: Int, val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val sel2  = Input(Bool())
    val in1   = Flipped(new SramIO(addrWidth, dataWidth))
    val in2   = Flipped(new SramIO(addrWidth, dataWidth))
    val out   = new SramIO(addrWidth, dataWidth)
  })

  io.in1.valid  := !io.sel2 && io.out.valid
  io.in1.fault  := !io.sel2 && io.out.fault
  io.in1.rdata  := io.out.rdata

  io.in2.valid  := io.sel2 && io.out.valid
  io.in2.fault  := io.sel2 && io.out.fault
  io.in2.rdata  := io.out.rdata

  io.out.en     := Mux(io.sel2, io.in2.en, io.in1.en)
  io.out.wen    := Mux(io.sel2, io.in2.wen, io.in1.wen)
  io.out.addr   := Mux(io.sel2, io.in2.addr, io.in1.addr)
  io.out.wdata  := Mux(io.sel2, io.in2.wdata, io.in1.wdata)
}
