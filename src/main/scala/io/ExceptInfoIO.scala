package io

import chisel3._

import consts.Parameters._
import consts.ExceptCause.EXC_CAUSE_WIDTH

class ExceptInfoIO extends Bundle {
  val hasTrap   = Bool()
  val isSret    = Bool()
  val isMret    = Bool()
  val excCause  = UInt(EXC_CAUSE_WIDTH.W)
  val excPc     = UInt(ADDR_WIDTH.W)
  val excValue  = UInt(DATA_WIDTH.W)
}
