package consts

import chisel3._
import chisel3.util.log2Ceil

object Constants {
  // general bus width
  val ADDR_WIDTH      = 32
  val DATA_WIDTH      = 32
  val INST_WIDTH      = 32

  // register file
  val REG_COUNT       = 32
  val REG_ADDR_WIDTH  = log2Ceil(REG_COUNT)

  // branch predictor
  val GHR_WIDTH       = 5
  val PHT_SIZE        = 1 << GHR_WIDTH

  // exception
  val RESET_PC        = "h00000200".U(ADDR_WIDTH.W)
}
