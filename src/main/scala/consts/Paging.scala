package consts

import chisel3._

object Paging {
  val SV32_PAGE_SHIFT = 12
  val SV32_PAGE_SIZE  = 1 << SV32_PAGE_SHIFT
}
