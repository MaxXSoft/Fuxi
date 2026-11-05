package io

import chisel3._

import consts.Architecture.ADDR_WIDTH
import org.chipsalliance.cde.config.Parameters
import configs.HasCoreParams

class BranchInfoIO(implicit val p: Parameters) extends Bundle with HasCoreParams {
  val branch  = Bool()             // enable training for this b/j
  val jump    = Bool()             // is 'jal' or 'jalr'
  val taken   = Bool()             // branch outcome; consume only when branch=true
  val index   = UInt(GHR_BITS.W)   // last index of PHT
  val pc      = UInt(ADDR_WIDTH.W) // last instruction PC
  val target  = UInt(ADDR_WIDTH.W) // last branch target
}
