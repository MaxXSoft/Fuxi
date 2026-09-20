package io

import chisel3._

import consts.Parameters._

class ExcMonCheckIO extends Bundle {
  val addr  = Output(UInt(ADDR_WIDTH.W))
  val valid = Input(Bool())
}

class ExcMonUpdateIO extends Bundle {
  val addr  = Output(UInt(ADDR_WIDTH.W))
  val set   = Output(Bool())
  val clear = Output(Bool())
  val clearAll = Output(Bool()) // every SC invalidates the reservation
}

class ExcMonCommitIO extends Bundle {
  val addr  = UInt(ADDR_WIDTH.W)
  val set   = Bool()
  val clear = Bool()
  val clearAll = Bool()
}
