package consts

import chisel3._

// exception type
object ExceptType extends ChiselEnum {
  val EXC_NONE    = Value
  val EXC_ECALL   = Value
  val EXC_EBRK    = Value
  val EXC_SRET    = Value
  val EXC_MRET    = Value
  val EXC_ILLEG   = Value // illegal instruction
  val EXC_IPAGE   = Value // instruction page fault
  val EXC_IADDR   = Value // instruction address misaligned
  val EXC_LOAD    = Value
  val EXC_STAMO   = Value
  val EXC_SPRIV   = Value // S-mode instruction
  val EXC_IACCESS = Value // instruction access fault
}

// exception cause
object ExceptCause {
  val EXC_CAUSE_WIDTH  = 31

  // interruptions
  val EXC_S_SOFT_INT   = 1.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_SOFT_INT   = 3.U(EXC_CAUSE_WIDTH.W)
  val EXC_S_TIMER_INT  = 5.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_TIMER_INT  = 7.U(EXC_CAUSE_WIDTH.W)
  val EXC_S_EXT_INT    = 9.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_EXT_INT    = 11.U(EXC_CAUSE_WIDTH.W)

  // exceptions
  val EXC_INST_ADDR    = 0.U(EXC_CAUSE_WIDTH.W)
  val EXC_INST_ACCESS  = 1.U(EXC_CAUSE_WIDTH.W)
  val EXC_ILL_INST     = 2.U(EXC_CAUSE_WIDTH.W)
  val EXC_BRK_POINT    = 3.U(EXC_CAUSE_WIDTH.W)
  val EXC_LOAD_ADDR    = 4.U(EXC_CAUSE_WIDTH.W)
  val EXC_LOAD_ACCESS  = 5.U(EXC_CAUSE_WIDTH.W)
  val EXC_STAMO_ADDR   = 6.U(EXC_CAUSE_WIDTH.W)
  val EXC_STAMO_ACCESS = 7.U(EXC_CAUSE_WIDTH.W)
  val EXC_U_ECALL      = 8.U(EXC_CAUSE_WIDTH.W)
  val EXC_S_ECALL      = 9.U(EXC_CAUSE_WIDTH.W)
  val EXC_M_ECALL      = 11.U(EXC_CAUSE_WIDTH.W)
  val EXC_INST_PAGE    = 12.U(EXC_CAUSE_WIDTH.W)
  val EXC_LOAD_PAGE    = 13.U(EXC_CAUSE_WIDTH.W)
  val EXC_STAMO_PAGE   = 15.U(EXC_CAUSE_WIDTH.W)
}
