package consts

import chisel3._
import chisel3.util.log2Ceil

object Constants {
  // general bus width
  val ADDR_WIDTH        = 32
  val ADDR_ALIGN_WIDTH  = log2Ceil(ADDR_WIDTH / 8)
  val DATA_WIDTH        = 32
  val INST_WIDTH        = 32

  // register file
  val REG_COUNT       = 32
  val REG_ADDR_WIDTH  = log2Ceil(REG_COUNT)

  // CSR
  val CSR_ADDR_WIDTH  = 12
  val CSR_MODE_WIDTH  = 2

  // branch predictor
  val GHR_WIDTH         = 5
  val PHT_SIZE          = 1 << GHR_WIDTH
  val BTB_INDEX_WIDTH   = 6
  val BTB_PC_WIDTH      = ADDR_WIDTH - BTB_INDEX_WIDTH - ADDR_ALIGN_WIDTH
  val BTB_TARGET_WIDTH  = ADDR_WIDTH - ADDR_ALIGN_WIDTH
  val BTB_SIZE          = 1 << BTB_INDEX_WIDTH

  // exception
  val RESET_PC  = "h00000200".U(ADDR_WIDTH.W)
}
