package consts

import chisel3._
import chisel3.util.log2Ceil

object Constants {
  val ADDR_WIDTH      = 32
  val DATA_WIDTH      = 32
  val INST_WIDTH      = 32

  val REG_COUNT       = 32
  val REG_ADDR_WIDTH  = log2Ceil(REG_COUNT)

  val RESET_PC        = "h00000200".U(ADDR_WIDTH.W)
}
