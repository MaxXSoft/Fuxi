package io

import chisel3._

import consts.Architecture.DATA_WIDTH
import consts.CSR.CSR_ADDR_WIDTH
import consts.CsrOp

class CsrReadIO extends Bundle {
  val op    = Output(CsrOp())
  val valid = Input(Bool())
  val addr  = Output(UInt(CSR_ADDR_WIDTH.W))
  val data  = Input(UInt(DATA_WIDTH.W))
}

class CsrWriteIO extends Bundle {
  val op      = Output(CsrOp())
  val addr    = Output(UInt(CSR_ADDR_WIDTH.W))
  val data    = Output(UInt(DATA_WIDTH.W))
  // instruction retired flag
  val retired = Output(Bool())
}

class CsrCommitIO extends Bundle {
  val op      = CsrOp()
  val addr    = UInt(CSR_ADDR_WIDTH.W)
  val data    = UInt(DATA_WIDTH.W)
  // instruction retired flag
  val retired = Bool()
}
