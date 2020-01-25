package io

import chisel3._

class SramIO(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val en    = Output(Bool())
  val valid = Input(Bool())
  val fault = Input(Bool())
  val wen   = Output(UInt((dataWidth / 8).W))
  val addr  = Output(UInt(addrWidth.W))
  val rdata = Input(UInt(dataWidth.W))
  val wdata = Output(UInt(dataWidth.W))
}
