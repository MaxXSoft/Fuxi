package consts

import chisel3._

// all operations that supported by ALU
object AluOp extends ChiselEnum {
  val ALU_ADD   = Value
  val ALU_SUB   = Value
  val ALU_XOR   = Value
  val ALU_OR    = Value
  val ALU_AND   = Value
  val ALU_SLT   = Value
  val ALU_SLTU  = Value
  val ALU_SLL   = Value
  val ALU_SRL   = Value
  val ALU_SRA   = Value
}

// all operations that supported by MDU
object MduOp extends ChiselEnum {
  val MDU_NOP     = Value
  val MDU_MUL     = Value
  val MDU_MULH    = Value
  val MDU_MULHSU  = Value
  val MDU_MULHU   = Value
  val MDU_DIV     = Value
  val MDU_DIVU    = Value
  val MDU_REM     = Value
  val MDU_REMU    = Value
}

// all operations that supported by LSU
object LsuOp extends ChiselEnum {
  val LSU_NOP   = Value
  val LSU_LB    = Value
  val LSU_LH    = Value
  val LSU_LW    = Value
  val LSU_LBU   = Value
  val LSU_LHU   = Value
  val LSU_SB    = Value
  val LSU_SH    = Value
  val LSU_SW    = Value
  val LSU_LR    = Value
  val LSU_SC    = Value
  val LSU_SWAP  = Value
  val LSU_ADD   = Value
  val LSU_XOR   = Value
  val LSU_AND   = Value
  val LSU_OR    = Value
  val LSU_MIN   = Value
  val LSU_MAX   = Value
  val LSU_MINU  = Value
  val LSU_MAXU  = Value
  val LSU_FENC  = Value
  val LSU_FENI  = Value
  val LSU_FENV  = Value
}

// all operations that supported by CSR
object CsrOp extends ChiselEnum {
  val CSR_NOP = Value
  val CSR_R   = Value
  val CSR_W   = Value
  val CSR_RW  = Value
  val CSR_RS  = Value
  val CSR_RC  = Value
}
