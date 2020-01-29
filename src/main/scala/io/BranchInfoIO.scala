package io

import chisel3._

import consts.Parameters.{GHR_WIDTH, ADDR_WIDTH}

class BranchInfoIO extends Bundle {
  val branch  = Bool()              // last inst is a b/j
  val jump    = Bool()              // is 'jal' or 'jalr'
  val taken   = Bool()              // is last branch taken
  val index   = UInt(GHR_WIDTH.W)   // last index of PHT
  val pc      = UInt(ADDR_WIDTH.W)  // last instruction PC
  val target  = UInt(ADDR_WIDTH.W)  // last branch target
}
