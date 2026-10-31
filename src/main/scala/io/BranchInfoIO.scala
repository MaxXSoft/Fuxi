package io

import chisel3._

import consts.Architecture.ADDR_WIDTH
import org.chipsalliance.cde.config.Parameters
import configs.CoreParamsKey

class BranchInfoIO(implicit p: Parameters) extends Bundle {
  val branch  = Bool()              // last inst is a b/j
  val jump    = Bool()              // is 'jal' or 'jalr'
  val taken   = Bool()              // is last branch taken
  val index   = UInt(p(CoreParamsKey).bpu.ghrBits.W)   // last index of PHT
  val pc      = UInt(ADDR_WIDTH.W)  // last instruction PC
  val target  = UInt(ADDR_WIDTH.W)  // last branch target
}
