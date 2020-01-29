package consts

import chisel3._

object CSR {
  // address
  val CSR_ADDR_WIDTH  = 12

  // privilege levels
  val CSR_MODE_WIDTH  = 2
  val CSR_MODE_U      = "b00".U(CSR_MODE_WIDTH.W)
  val CSR_MODE_S      = "b01".U(CSR_MODE_WIDTH.W)
  val CSR_MODE_M      = "b11".U(CSR_MODE_WIDTH.W)

  //
}
