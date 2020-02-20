package consts

import chisel3._
import chisel3.util.log2Ceil

object Parameters {
  // general bus width
  val ADDR_WIDTH        = 32
  val ADDR_ALIGN_WIDTH  = log2Ceil(ADDR_WIDTH / 8)
  val DATA_WIDTH        = 32
  val INST_WIDTH        = 32

  // register file
  val REG_COUNT       = 32
  val REG_ADDR_WIDTH  = log2Ceil(REG_COUNT)

  // branch predictor
  val GHR_WIDTH         = 5
  val PHT_SIZE          = 1 << GHR_WIDTH
  val BTB_INDEX_WIDTH   = 6
  val BTB_PC_WIDTH      = ADDR_WIDTH - BTB_INDEX_WIDTH - ADDR_ALIGN_WIDTH
  val BTB_TARGET_WIDTH  = ADDR_WIDTH - ADDR_ALIGN_WIDTH
  val BTB_SIZE          = 1 << BTB_INDEX_WIDTH

  // exception
  val RESET_PC  = "h00000200".U(ADDR_WIDTH.W)

  // TLB
  val ITLB_WIDTH  = 4                 // 2^4 = 16 entries
  val ITLB_SIZE   = 1 << ITLB_WIDTH
  val DTLB_WIDTH  = 4                 // 2^4 = 16 entries
  val DTLB_SIZE   = 1 << DTLB_WIDTH

  // cache
  val ICACHE_WIDTH      = 6                       // 2^6 = 64 lines
  val ICACHE_SIZE       = 1 << ICACHE_WIDTH
  val ICACHE_LINE_WIDTH = 6                       // 2^6 = 64 bytes/line
  val ICACHE_LINE_SIZE  = 1 << ICACHE_LINE_WIDTH
  val DCACHE_WIDTH      = 7                       // 2^7 = 128 lines
  val DCACHE_SIZE       = 1 << DCACHE_WIDTH
  val DCACHE_LINE_WIDTH = 6                       // 2^6 = 64 bytes/line
  val DCACHE_LINE_SIZE  = 1 << DCACHE_LINE_WIDTH

  // uncached address space
  val UNCACHED_ADDR_START = "h10000000".U(ADDR_WIDTH.W)
  val UNCACHED_ADDR_END   = "h20000000".U(ADDR_WIDTH.W)
}
