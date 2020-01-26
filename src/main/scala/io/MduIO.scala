package io

import chisel3._

import consts.Constants._
import consts.MduOp.MDU_OP_WIDTH

class MduIO extends Bundle {
  val flush   = Output(Bool())
  val op      = Output(UInt(MDU_OP_WIDTH.W))
  val valid   = Input(Bool())
  val opr1    = Output(UInt(DATA_WIDTH.W))
  val opr2    = Output(UInt(DATA_WIDTH.W))
  val result  = Input(UInt(DATA_WIDTH.W))
}
