package io

import chisel3._

import consts.Parameters._

class DebugIO extends Bundle {
  val regWen    = Output(Bool())
  val regWaddr  = Output(UInt(REG_ADDR_WIDTH.W))
  val regWdata  = Output(UInt(DATA_WIDTH.W))
  val pc        = Output(UInt(ADDR_WIDTH.W))
}
