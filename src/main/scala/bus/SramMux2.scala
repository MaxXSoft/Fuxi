package bus

import chisel3._

import io.SramIO

class SramMux2(val addrWidth: Int, val dataWidth: Int,
               val sel2: UInt => Bool) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(new SramIO(addrWidth, dataWidth))
    val out1  = new SramIO(addrWidth, dataWidth)
    val out2  = new SramIO(addrWidth, dataWidth)
  })

  val out2En  = sel2(io.in.addr)

  io.in.valid := Mux(out2En, io.out2.valid, io.out1.valid)
  io.in.fault := Mux(out2En, io.out2.fault, io.out1.fault)
  io.in.rdata := Mux(out2En, io.out2.rdata, io.out1.rdata)

  io.out1.en    := !out2En
  io.out1.wen   := io.in.wen
  io.out1.addr  := io.in.addr
  io.out1.wdata := io.in.wdata

  io.out2.en    := out2En
  io.out2.wen   := io.in.wen
  io.out2.addr  := io.in.addr
  io.out2.wdata := io.in.wdata
}
