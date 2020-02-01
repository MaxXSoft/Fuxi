package consts

import chisel3._
import chisel3.util.log2Ceil

// all operations that supported by ALU
object AluOp {
  val ALU_OP_WIDTH = log2Ceil(10)

  val ALU_ADD   = 0.U(ALU_OP_WIDTH.W)
  val ALU_SUB   = 1.U(ALU_OP_WIDTH.W)
  val ALU_XOR   = 2.U(ALU_OP_WIDTH.W)
  val ALU_OR    = 3.U(ALU_OP_WIDTH.W)
  val ALU_AND   = 4.U(ALU_OP_WIDTH.W)
  val ALU_SLT   = 5.U(ALU_OP_WIDTH.W)
  val ALU_SLTU  = 6.U(ALU_OP_WIDTH.W)
  val ALU_SLL   = 7.U(ALU_OP_WIDTH.W)
  val ALU_SRL   = 8.U(ALU_OP_WIDTH.W)
  val ALU_SRA   = 9.U(ALU_OP_WIDTH.W)
}

// all operations that supported by MDU
object MduOp {
  val MDU_OP_WIDTH = log2Ceil(9)

  val MDU_NOP     = 0.U(MDU_OP_WIDTH.W)
  val MDU_MUL     = 1.U(MDU_OP_WIDTH.W)
  val MDU_MULH    = 2.U(MDU_OP_WIDTH.W)
  val MDU_MULHSU  = 3.U(MDU_OP_WIDTH.W)
  val MDU_MULHU   = 4.U(MDU_OP_WIDTH.W)
  val MDU_DIV     = 5.U(MDU_OP_WIDTH.W)
  val MDU_DIVU    = 6.U(MDU_OP_WIDTH.W)
  val MDU_REM     = 7.U(MDU_OP_WIDTH.W)
  val MDU_REMU    = 8.U(MDU_OP_WIDTH.W)
}

// all operations that supported by LSU
object LsuOp {
  val LSU_OP_WIDTH = log2Ceil(23)

  val LSU_NOP   = 0.U(LSU_OP_WIDTH.W)
  val LSU_LB    = 1.U(LSU_OP_WIDTH.W)
  val LSU_LH    = 2.U(LSU_OP_WIDTH.W)
  val LSU_LW    = 3.U(LSU_OP_WIDTH.W)
  val LSU_LBU   = 4.U(LSU_OP_WIDTH.W)
  val LSU_LHU   = 5.U(LSU_OP_WIDTH.W)
  val LSU_SB    = 6.U(LSU_OP_WIDTH.W)
  val LSU_SH    = 7.U(LSU_OP_WIDTH.W)
  val LSU_SW    = 8.U(LSU_OP_WIDTH.W)
  val LSU_LR    = 9.U(LSU_OP_WIDTH.W)
  val LSU_SC    = 10.U(LSU_OP_WIDTH.W)
  val LSU_SWAP  = 11.U(LSU_OP_WIDTH.W)
  val LSU_ADD   = 12.U(LSU_OP_WIDTH.W)
  val LSU_XOR   = 13.U(LSU_OP_WIDTH.W)
  val LSU_AND   = 14.U(LSU_OP_WIDTH.W)
  val LSU_OR    = 15.U(LSU_OP_WIDTH.W)
  val LSU_MIN   = 16.U(LSU_OP_WIDTH.W)
  val LSU_MAX   = 17.U(LSU_OP_WIDTH.W)
  val LSU_MINU  = 18.U(LSU_OP_WIDTH.W)
  val LSU_MAXU  = 19.U(LSU_OP_WIDTH.W)
  val LSU_FENC  = 20.U(LSU_OP_WIDTH.W)
  val LSU_FENI  = 21.U(LSU_OP_WIDTH.W)
  val LSU_FENV  = 22.U(LSU_OP_WIDTH.W)
}

// all operations that supported by CSR
object CsrOp {
  val CSR_OP_WIDTH = log2Ceil(6)

  val CSR_NOP = 0.U(CSR_OP_WIDTH.W)
  val CSR_R   = 1.U(CSR_OP_WIDTH.W)
  val CSR_W   = 2.U(CSR_OP_WIDTH.W)
  val CSR_RW  = 3.U(CSR_OP_WIDTH.W)
  val CSR_RS  = 4.U(CSR_OP_WIDTH.W)
  val CSR_RC  = 5.U(CSR_OP_WIDTH.W)
}
