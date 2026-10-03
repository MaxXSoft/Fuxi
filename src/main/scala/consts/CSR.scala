package consts

import chisel3._

object CSR {
  // privilege levels
  val CSR_MODE_WIDTH  = 2
  val CSR_MODE_U      = "b00".U(CSR_MODE_WIDTH.W)
  val CSR_MODE_S      = "b01".U(CSR_MODE_WIDTH.W)
  val CSR_MODE_M      = "b11".U(CSR_MODE_WIDTH.W)

  // address
  val CSR_ADDR_WIDTH  = 12
  // user counters (read only)
  val CSR_CYCLE       = 0xc00.U(CSR_ADDR_WIDTH.W)
  val CSR_TIME        = 0xc01.U(CSR_ADDR_WIDTH.W)
  val CSR_INSTRET     = 0xc02.U(CSR_ADDR_WIDTH.W)
  val CSR_CYCLEH      = 0xc80.U(CSR_ADDR_WIDTH.W)
  val CSR_TIMEH       = 0xc81.U(CSR_ADDR_WIDTH.W)
  val CSR_INSTRETH    = 0xc82.U(CSR_ADDR_WIDTH.W)
  // supervisor trap setup
  val CSR_SSTATUS     = 0x100.U(CSR_ADDR_WIDTH.W)
  val CSR_SIE         = 0x104.U(CSR_ADDR_WIDTH.W)
  val CSR_STVEC       = 0x105.U(CSR_ADDR_WIDTH.W)
  val CSR_SCOUNTEREN  = 0x106.U(CSR_ADDR_WIDTH.W)
  // supervisor trap handling
  val CSR_SSCRATCH    = 0x140.U(CSR_ADDR_WIDTH.W)
  val CSR_SEPC        = 0x141.U(CSR_ADDR_WIDTH.W)
  val CSR_SCAUSE      = 0x142.U(CSR_ADDR_WIDTH.W)
  val CSR_STVAL       = 0x143.U(CSR_ADDR_WIDTH.W)
  val CSR_SIP         = 0x144.U(CSR_ADDR_WIDTH.W)
  // supervisor protection and translation
  val CSR_SATP        = 0x180.U(CSR_ADDR_WIDTH.W)
  // machine information registers (read only)
  val CSR_MVENDERID   = 0xf11.U(CSR_ADDR_WIDTH.W)
  val CSR_MARCHID     = 0xf12.U(CSR_ADDR_WIDTH.W)
  val CSR_MIMPID      = 0xf13.U(CSR_ADDR_WIDTH.W)
  val CSR_MHARTID     = 0xf14.U(CSR_ADDR_WIDTH.W)
  // machine trap setup
  val CSR_MSTATUS     = 0x300.U(CSR_ADDR_WIDTH.W)
  val CSR_MISA        = 0x301.U(CSR_ADDR_WIDTH.W)
  val CSR_MSTATUSH    = 0x310.U(CSR_ADDR_WIDTH.W)
  val CSR_MEDELEG     = 0x302.U(CSR_ADDR_WIDTH.W)
  val CSR_MIDELEG     = 0x303.U(CSR_ADDR_WIDTH.W)
  val CSR_MIE         = 0x304.U(CSR_ADDR_WIDTH.W)
  val CSR_MTVEC       = 0x305.U(CSR_ADDR_WIDTH.W)
  val CSR_MCOUNTEREN  = 0x306.U(CSR_ADDR_WIDTH.W)
  // machine trap handling
  val CSR_MSCRATCH    = 0x340.U(CSR_ADDR_WIDTH.W)
  val CSR_MEPC        = 0x341.U(CSR_ADDR_WIDTH.W)
  val CSR_MCAUSE      = 0x342.U(CSR_ADDR_WIDTH.W)
  val CSR_MTVAL       = 0x343.U(CSR_ADDR_WIDTH.W)
  val CSR_MIP         = 0x344.U(CSR_ADDR_WIDTH.W)
  // machine counters
  val CSR_MCYCLE      = 0xb00.U(CSR_ADDR_WIDTH.W)
  val CSR_MINSTRET    = 0xb02.U(CSR_ADDR_WIDTH.W)
  val CSR_MCYCLEH     = 0xb80.U(CSR_ADDR_WIDTH.W)
  val CSR_MINSTRETH   = 0xb82.U(CSR_ADDR_WIDTH.W)
  // machine counter setup
  val CSR_MCOUNTINHIBIT = 0x320.U(CSR_ADDR_WIDTH.W)

  def hpmCounterAddr(n: Int): UInt = {
    require(n >= 3 && n <= 31)
    (0xc00 + n).U(CSR_ADDR_WIDTH.W)
  }

  def hpmCounterHighAddr(n: Int): UInt = {
    require(n >= 3 && n <= 31)
    (0xc80 + n).U(CSR_ADDR_WIDTH.W)
  }

  def mhpmCounterAddr(n: Int): UInt = {
    require(n >= 3 && n <= 31)
    (0xb00 + n).U(CSR_ADDR_WIDTH.W)
  }

  def mhpmCounterHighAddr(n: Int): UInt = {
    require(n >= 3 && n <= 31)
    (0xb80 + n).U(CSR_ADDR_WIDTH.W)
  }

  def mhpmEventAddr(n: Int): UInt = {
    require(n >= 3 && n <= 31)
    (0x320 + n).U(CSR_ADDR_WIDTH.W)
  }

  // RV32 PMP addresses currently exposed by Fuxi (WARL zero).
  def pmpCfgAddr(n: Int): UInt = {
    require(n >= 0 && n < 4)
    (0x3a0 + n).U(CSR_ADDR_WIDTH.W)
  }

  def pmpAddr(n: Int): UInt = {
    require(n >= 0 && n < 16)
    (0x3b0 + n).U(CSR_ADDR_WIDTH.W)
  }

  def requiredPrivilege(addr: UInt): UInt = addr(9, 8)

  // Architectural counter number N, shared by low/high halves and aliases.
  // This is not the HPM storage index N - 3. TIME (N = 1) has only user aliases.
  def counterIndex(addr: UInt): UInt = addr(4, 0)

  private def hasCounterOffset(addr: UInt): Bool =
    addr(6, 5) === 0.U

  def isMachineCounter(addr: UInt): Bool =
    addr(11, 8) === 0xb.U && hasCounterOffset(addr) && counterIndex(addr) =/= 1.U

  def isUserCounter(addr: UInt): Bool =
    addr(11, 8) === 0xc.U && hasCounterOffset(addr)

  def isCounter(addr: UInt): Bool = isMachineCounter(addr) || isUserCounter(addr)

  def isHpmEvent(addr: UInt): Bool =
    addr >= mhpmEventAddr(3) && addr <= mhpmEventAddr(31)

  def isCounterEnable(addr: UInt): Bool =
    addr === CSR_MCOUNTEREN || addr === CSR_SCOUNTEREN

  def isInstretOrHpmCounter(addr: UInt): Bool =
    isCounter(addr) && counterIndex(addr) >= 2.U
}
