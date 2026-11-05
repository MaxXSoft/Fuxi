package io

import chisel3._

import consts.Architecture._

class RegReadIO extends Bundle {
  // Whether this operand is consumed. Data need not be zero while disabled;
  // forwarding may resolve it early, before the decoder produces this enable.
  val en    = Output(Bool())
  val addr  = Output(UInt(REG_ADDR_WIDTH.W))
  val data  = Input(UInt(DATA_WIDTH.W))
}

class RegWriteIO extends Bundle {
  val en    = Output(Bool())
  val addr  = Output(UInt(REG_ADDR_WIDTH.W))
  val data  = Output(UInt(DATA_WIDTH.W))
}

class RegCommitIO extends Bundle {
  val en    = Bool()
  val addr  = UInt(REG_ADDR_WIDTH.W)
  val data  = UInt(DATA_WIDTH.W)
  // detect load hazard
  val load  = Bool()
}
