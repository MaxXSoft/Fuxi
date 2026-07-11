package io

import chisel3._

import consts.Parameters._
import consts.ExceptCause.EXC_CAUSE_WIDTH

class ExceptInfoIO extends Bundle {
  val hasTrap   = Bool()
  // True when this trap record represents an interrupt selected at a precise
  // instruction boundary, rather than a synchronous exception or xRET.
  val isInterrupt = Bool()
  val isSret    = Bool()
  val isMret    = Bool()
  val excCause  = UInt(EXC_CAUSE_WIDTH.W)
  val excPc     = UInt(ADDR_WIDTH.W)
  val excValue  = UInt(DATA_WIDTH.W)
}
