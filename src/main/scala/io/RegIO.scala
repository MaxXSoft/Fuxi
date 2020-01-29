package io

import chisel3._

import consts.Parameters._

class RegReadIO extends Bundle {
  val en    = Output(Bool())
  val addr  = Output(UInt(REG_ADDR_WIDTH.W))
  val data  = Input(UInt(DATA_WIDTH.W))
}

class RegWriteIO extends Bundle {
  val en    = Output(Bool())
  val addr  = Output(UInt(REG_ADDR_WIDTH.W))
  val data  = Output(UInt(DATA_WIDTH.W))
}
