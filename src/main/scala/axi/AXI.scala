package axi

import chisel3._
import chisel3.util.Decoupled

// reference: NSP on https://dev.tencent.com/u/linjiav/p/NSP/git

class AxiAddrChannel(val addrWidth: Int) extends Bundle {
  // required fields
  val addr  = UInt(addrWidth.W)
  val id    = UInt(4.W)
  val size  = UInt(3.W)
  val len   = UInt(8.W)
  val burst = UInt(2.W)
  // optional fields
  val lock  = Bool()
  val cache = UInt(4.W)
  val prot  = UInt(3.W)
}

abstract class AxiDataChannel(val dataWidth: Int) extends Bundle {
  val data  = UInt(dataWidth.W)
  val id    = UInt(4.W)
  val last  = Bool()
}

class AxiReadDataChannel(dataWidth: Int)
      extends AxiDataChannel(dataWidth) {
  val resp  = UInt(2.W)
}

class AxiWriteDataChannel(dataWidth: Int)
      extends AxiDataChannel(dataWidth) {
  val strb  = UInt((dataWidth / 8).W)
}

class AxiWriteRespChannel extends Bundle {
  val id    = UInt(4.W)
  val resp  = UInt(2.W)
}

class AxiMaster(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val readAddr  = Decoupled(new AxiAddrChannel(addrWidth))
  val readData  = Flipped(Decoupled(new AxiReadDataChannel(dataWidth)))
  val writeAddr = Decoupled(new AxiAddrChannel(addrWidth))
  val writeData = Decoupled(new AxiWriteDataChannel(dataWidth))
  val writeResp = Flipped(Decoupled(new AxiWriteRespChannel))
}
