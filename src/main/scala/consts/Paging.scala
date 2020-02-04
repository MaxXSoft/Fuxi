package consts

import chisel3._

import consts.Parameters.ADDR_WIDTH

object Paging {
  // page table related constants
  val PAGE_SHIFT  = 12
  val PAGE_SIZE   = 1 << PAGE_SHIFT

  // physical address & virtual address
  val PPN_WIDTH         = 22
  val VPN_WIDTH         = 20
  val PAGE_OFFSET_WIDTH = PAGE_SHIFT
  require(VPN_WIDTH == ADDR_WIDTH - PAGE_OFFSET_WIDTH)
}
