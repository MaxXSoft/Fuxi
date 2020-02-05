package consts

import chisel3._
import chisel3.util._

import consts.Parameters.ADDR_WIDTH

object Paging {
  // physical address & virtual address
  val PPN_WIDTH         = 22
  val PAGE_OFFSET_WIDTH = 12
  val VPN_WIDTH         = ADDR_WIDTH - PAGE_OFFSET_WIDTH

  // address translation related constants
  val PAGE_SHIFT  = PAGE_OFFSET_WIDTH
  val PAGE_SIZE   = 1 << PAGE_SHIFT
  val PTE_SHIFT   = 2
  val PTE_SIZE    = 1 << PTE_SHIFT
  val PTE_WIDTH   = PPN_WIDTH + 10
  val LEVELS      = 2
  val LEVEL_WIDTH = log2Ceil(LEVELS)

  // get vpn[i]
  def getVpn(vaddr: UInt, index: UInt) =
      Mux(index === 1.U, vaddr(31, 22), vaddr(21, 12))

  // get vpn[i:0]
  def getVpnToZero(vaddr: UInt, index: UInt) =
      Mux(index === 1.U, vaddr(31, 12), vaddr(21, 12))

  // get ppn[i]
  def getPpn(ppn: UInt, index: UInt) =
      Mux(index === 1.U, ppn(21, 10), ppn(9, 0))

  // get ppn[i:0]
  def getPpnToZero(ppn: UInt, index: UInt) =
      Mux(index === 1.U, ppn(21, 0), ppn(9, 0))

  // get address of PTE
  def getPteAddr(ppn: UInt, vaddr: UInt, index: UInt) =
      (ppn << PAGE_SHIFT) + (getVpn(vaddr, index) << PTE_SHIFT)
  
  // get PPN of superpage via PTE's PPN
  def getSuperPpn(ppn: UInt, vaddr: UInt, index: UInt) =
      (ppn ^ getPpnToZero(ppn, index)) | getVpnToZero(vaddr, index)
}
