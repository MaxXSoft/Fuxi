package lsu

import chisel3._
import chisel3.util._

import consts.LsuOp._
import consts.Control.{Y, N}

// Data width selected by load/store decode and carried to writeback.
object LsuWidth extends ChiselEnum {
  val LS_DATA_BYTE, LS_DATA_HALF, LS_DATA_WORD = Value
}

// Operations of the ALU for AMO instructions.
object AmoOp extends ChiselEnum {
  val AMO_OP_NOP  = Value
  val AMO_OP_SWAP = Value
  val AMO_OP_ADD  = Value
  val AMO_OP_XOR  = Value
  val AMO_OP_AND  = Value
  val AMO_OP_OR   = Value
  val AMO_OP_MIN  = Value
  val AMO_OP_MAX  = Value
  val AMO_OP_MINU = Value
  val AMO_OP_MAXU = Value
}

object LsuDecode {
  import AmoOp._
  import LsuWidth._

  // decode logic
  val DEFAULT =
  //                               load                setEm                    fiTlb
  //                          en wen |     width  signed |chkEm  amoOp    fi$ fd$ |fdTlb
  //                           |  |  |       |        |  |  |      |        |  |  |  |
                          List(N, N, N, LS_DATA_BYTE, N, N, N, AMO_OP_NOP,  N, N, N, N)
  val TABLE = Array(
    BitPat(LSU_LB.asUInt)    ->  List(Y, N, Y, LS_DATA_BYTE, Y, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_LH.asUInt)    ->  List(Y, N, Y, LS_DATA_HALF, Y, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_LW.asUInt)    ->  List(Y, N, Y, LS_DATA_WORD, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_LBU.asUInt)   ->  List(Y, N, Y, LS_DATA_BYTE, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_LHU.asUInt)   ->  List(Y, N, Y, LS_DATA_HALF, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_SB.asUInt)    ->  List(Y, Y, N, LS_DATA_BYTE, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_SH.asUInt)    ->  List(Y, Y, N, LS_DATA_HALF, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_SW.asUInt)    ->  List(Y, Y, N, LS_DATA_WORD, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_LR.asUInt)    ->  List(Y, N, Y, LS_DATA_WORD, N, Y, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_SC.asUInt)    ->  List(Y, N, N, LS_DATA_WORD, N, N, Y, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_SWAP.asUInt)  ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_SWAP, N, N, N, N),
    BitPat(LSU_ADD.asUInt)   ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_ADD,  N, N, N, N),
    BitPat(LSU_XOR.asUInt)   ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_XOR,  N, N, N, N),
    BitPat(LSU_AND.asUInt)   ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_AND,  N, N, N, N),
    BitPat(LSU_OR.asUInt)    ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_OR,   N, N, N, N),
    BitPat(LSU_MIN.asUInt)   ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_MIN,  N, N, N, N),
    BitPat(LSU_MAX.asUInt)   ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_MAX,  N, N, N, N),
    BitPat(LSU_MINU.asUInt)  ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_MINU, N, N, N, N),
    BitPat(LSU_MAXU.asUInt)  ->  List(Y, N, N, LS_DATA_WORD, N, N, N, AMO_OP_MAXU, N, N, N, N),
    BitPat(LSU_FENC.asUInt)  ->  List(N, N, N, LS_DATA_BYTE, N, N, N, AMO_OP_NOP,  N, N, N, N),
    BitPat(LSU_FENI.asUInt)  ->  List(N, N, N, LS_DATA_BYTE, N, N, N, AMO_OP_NOP,  Y, Y, N, N),
    BitPat(LSU_FENV.asUInt)  ->  List(N, N, N, LS_DATA_BYTE, N, N, N, AMO_OP_NOP,  Y, Y, Y, Y),
  )
}
