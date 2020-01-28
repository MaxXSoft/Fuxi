package mdu

import chisel3.util.BitPat

import consts.MduOp._
import consts.Control.{Y, N}

object MduDecode {
  val DEFAULT =
  //                              den hi/rm rsn
  //                            men |  | lsn |
  //                             |  |  |  |  |
                            List(N, N, N, N, N)
  val TABLE   = Array(
    BitPat(MDU_MUL)     ->  List(Y, N, N, N, N),
    BitPat(MDU_MULH)    ->  List(Y, N, Y, Y, Y),
    BitPat(MDU_MULHSU)  ->  List(Y, N, Y, Y, N),
    BitPat(MDU_MULHU)   ->  List(Y, N, Y, N, N),
    BitPat(MDU_DIV)     ->  List(N, Y, N, Y, Y),
    BitPat(MDU_DIVU)    ->  List(N, Y, N, N, N),
    BitPat(MDU_REM)     ->  List(N, Y, Y, Y, Y),
    BitPat(MDU_REMU)    ->  List(N, Y, Y, N, N),
  )
}
