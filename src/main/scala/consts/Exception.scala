package consts

import chisel3._
import chisel3.util.log2Ceil

// exception type
object ExceptType {
  val EXC_TYPE_WIDTH = log2Ceil(11)

  val EXC_NONE  = 0.U(EXC_TYPE_WIDTH.W)
  val EXC_ECALL = 1.U(EXC_TYPE_WIDTH.W)
  val EXC_EBRK  = 2.U(EXC_TYPE_WIDTH.W)
  val EXC_SRET  = 3.U(EXC_TYPE_WIDTH.W)
  val EXC_MRET  = 4.U(EXC_TYPE_WIDTH.W)
  val EXC_ILLEG = 5.U(EXC_TYPE_WIDTH.W)   // illegal instruction
  val EXC_IPAGE = 6.U(EXC_TYPE_WIDTH.W)   // instruction page fault
  val EXC_IADDR = 7.U(EXC_TYPE_WIDTH.W)   // instruction address misaligned
  val EXC_LOAD  = 8.U(EXC_TYPE_WIDTH.W)
  val EXC_STAMO = 9.U(EXC_TYPE_WIDTH.W)
  val EXC_SPRIV = 10.U(EXC_TYPE_WIDTH.W)  // S-mode instruction
}

// exception cause
object ExceptCause {
  val EXC_CAUSE_WIDTH = 31

  // interruptions
  val EXC_S_SOFT_INT  = 1.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_SOFT_INT  = 3.U(EXC_CAUSE_WIDTH.W)
  val EXC_S_TIMER_INT = 5.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_TIMER_INT = 7.U(EXC_CAUSE_WIDTH.W)
  val EXC_S_EXT_INT   = 9.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_EXT_INT   = 11.U(EXC_CAUSE_WIDTH.W)

  // exceptions
  val EXC_INST_ADDR   = 0.U(EXC_CAUSE_WIDTH.W)
  val EXC_ILL_INST    = 2.U(EXC_CAUSE_WIDTH.W)
  val EXC_BRK_POINT   = 3.U(EXC_CAUSE_WIDTH.W)
  val EXC_LOAD_ADDR   = 4.U(EXC_CAUSE_WIDTH.W)
  val EXC_STAMO_ADDR  = 6.U(EXC_CAUSE_WIDTH.W)
  val EXC_U_ECALL     = 8.U(EXC_CAUSE_WIDTH.W)
  val EXC_S_ECALL     = 9.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_ECALL     = 11.U(EXC_CAUSE_WIDTH.W)
  val EXC_INST_PAGE   = 12.U(EXC_CAUSE_WIDTH.W)
  val EXC_LOAD_PAGE   = 13.U(EXC_CAUSE_WIDTH.W)
  val EXC_STAMO_PAGE  = 15.U(EXC_CAUSE_WIDTH.W)
}
