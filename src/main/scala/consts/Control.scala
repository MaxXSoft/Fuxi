package consts

import chisel3._
import chisel3.util.log2Ceil

import Instructions._
import AluOp._
import MduOp._
import LsuOp._
import CsrOp._
import ExceptType._

object Control {
  // true/false
  val Y = true.B
  val N = false.B

  // operand selector
  val OPR_WIDTH = log2Ceil(9)
  val OPR_ZERO  = 0.U(OPR_WIDTH.W)
  val OPR_REG1  = 1.U(OPR_WIDTH.W)
  val OPR_REG2  = 2.U(OPR_WIDTH.W)
  val OPR_IMMI  = 3.U(OPR_WIDTH.W)
  val OPR_IMMS  = 4.U(OPR_WIDTH.W)
  val OPR_IMMU  = 5.U(OPR_WIDTH.W)  // with zeroed low 12-bit
  val OPR_IMMR  = 6.U(OPR_WIDTH.W)
  val OPR_PC    = 7.U(OPR_WIDTH.W)
  val OPR_4     = 8.U(OPR_WIDTH.W)

  // branch operation
  val BR_WIDTH  = log2Ceil(8)
  val BR_N      = 0.U(BR_WIDTH.W)
  val BR_AL     = 1.U(BR_WIDTH.W)
  val BR_EQ     = 2.U(BR_WIDTH.W)
  val BR_NE     = 3.U(BR_WIDTH.W)
  val BR_LT     = 4.U(BR_WIDTH.W)
  val BR_GE     = 5.U(BR_WIDTH.W)
  val BR_LTU    = 6.U(BR_WIDTH.W)
  val BR_GEU    = 7.U(BR_WIDTH.W)

  // table of control signals
  val DEFAULT =
  //                     reg2
  //                  reg1| wen  aluOpr1   aluOpr2   aluOp    branch   lsuOp     csrOp     mduOp       excType
  //                   |  |  |      |         |        |        |        |         |         |            |
                  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_ILLEG)
  val TABLE   = Array(
    ADD       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    ADDI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SUB       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_SUB,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    LUI       ->  List(N, N, Y, OPR_ZERO, OPR_IMMU, ALU_OR,   BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    AUIPC     ->  List(N, N, Y, OPR_PC,   OPR_IMMU, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    XOR       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_XOR,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    XORI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_XOR,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    OR        ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_OR,   BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    ORI       ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_OR,   BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    AND       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_AND,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    ANDI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_AND,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SLT       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_SLT,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SLTI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_SLT,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SLTU      ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_SLTU, BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SLTIU     ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_SLTU, BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SLL       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_SLL,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SLLI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMR, ALU_SLL,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SRL       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_SRL,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SRLI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMR, ALU_SRL,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SRA       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_SRA,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SRAI      ->  List(Y, N, Y, OPR_REG1, OPR_IMMR, ALU_SRA,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    BEQ       ->  List(Y, Y, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_EQ,  LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    BNE       ->  List(Y, Y, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_NE,  LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    BLT       ->  List(Y, Y, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_LT,  LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    BGE       ->  List(Y, Y, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_GE,  LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    BLTU      ->  List(Y, Y, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_LTU, LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    BGEU      ->  List(Y, Y, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_GEU, LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    JAL       ->  List(N, N, Y, OPR_PC,   OPR_4,    ALU_ADD,  BR_AL,  LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    JALR      ->  List(Y, N, Y, OPR_PC,   OPR_4,    ALU_ADD,  BR_AL,  LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    LB        ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_ADD,  BR_N,   LSU_LB,   CSR_NOP,  MDU_NOP,    EXC_LOAD),
    LH        ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_ADD,  BR_N,   LSU_LH,   CSR_NOP,  MDU_NOP,    EXC_LOAD),
    LW        ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_ADD,  BR_N,   LSU_LW,   CSR_NOP,  MDU_NOP,    EXC_LOAD),
    LBU       ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_ADD,  BR_N,   LSU_LBU,  CSR_NOP,  MDU_NOP,    EXC_LOAD),
    LHU       ->  List(Y, N, Y, OPR_REG1, OPR_IMMI, ALU_ADD,  BR_N,   LSU_LHU,  CSR_NOP,  MDU_NOP,    EXC_LOAD),
    SB        ->  List(Y, Y, N, OPR_REG1, OPR_IMMS, ALU_ADD,  BR_N,   LSU_SB,   CSR_NOP,  MDU_NOP,    EXC_STAMO),
    SH        ->  List(Y, Y, N, OPR_REG1, OPR_IMMS, ALU_ADD,  BR_N,   LSU_SH,   CSR_NOP,  MDU_NOP,    EXC_STAMO),
    SW        ->  List(Y, Y, N, OPR_REG1, OPR_IMMS, ALU_ADD,  BR_N,   LSU_SW,   CSR_NOP,  MDU_NOP,    EXC_STAMO),
    FENCE     ->  List(N, N, N, OPR_ZERO, OPR_IMMI, ALU_OR,   BR_N,   LSU_FENC, CSR_NOP,  MDU_NOP,    EXC_NONE),
    FENCEI    ->  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_FENI, CSR_NOP,  MDU_NOP,    EXC_NONE),
    CSRRW     ->  List(Y, N, Y, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_RW,   MDU_NOP,    EXC_NONE),
    CSRRS     ->  List(Y, N, Y, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_RS,   MDU_NOP,    EXC_NONE),
    CSRRC     ->  List(Y, N, Y, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_RC,   MDU_NOP,    EXC_NONE),
    CSRRWI    ->  List(N, N, Y, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_RW,   MDU_NOP,    EXC_NONE),
    CSRRSI    ->  List(N, N, Y, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_RS,   MDU_NOP,    EXC_NONE),
    CSRRCI    ->  List(N, N, Y, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_RC,   MDU_NOP,    EXC_NONE),
    MUL       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_MUL,    EXC_NONE),
    MULH      ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_MULH,   EXC_NONE),
    MULHSU    ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_MULHSU, EXC_NONE),
    MULHU     ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_MULHU,  EXC_NONE),
    DIV       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_DIV,    EXC_NONE),
    DIVU      ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_DIVU,   EXC_NONE),
    REM       ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_REM,    EXC_NONE),
    REMU      ->  List(Y, Y, Y, OPR_REG1, OPR_REG2, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_REMU,   EXC_NONE),
    LRW       ->  List(Y, N, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_LR,   CSR_NOP,  MDU_NOP,    EXC_STAMO),
    SCW       ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_SC,   CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOSWAPW  ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_SWAP, CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOADDW   ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_ADD,  CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOXORW   ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_XOR,  CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOANDW   ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_AND,  CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOORW    ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_OR,   CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOMINW   ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_MIN,  CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOMAXW   ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_MAX,  CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOMINUW  ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_MINU, CSR_NOP,  MDU_NOP,    EXC_STAMO),
    AMOMAXUW  ->  List(Y, Y, Y, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_MAXU, CSR_NOP,  MDU_NOP,    EXC_STAMO),
    ECALL     ->  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_ECALL),
    EBREAK    ->  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_EBRK),
    SRET      ->  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_SRET),
    MRET      ->  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_MRET),
    WFI       ->  List(N, N, N, OPR_ZERO, OPR_ZERO, ALU_ADD,  BR_N,   LSU_NOP,  CSR_NOP,  MDU_NOP,    EXC_NONE),
    SFENCEVMA ->  List(Y, Y, N, OPR_REG1, OPR_ZERO, ALU_OR,   BR_N,   LSU_FENV, CSR_NOP,  MDU_NOP,    EXC_SPRIV),
  )
}
