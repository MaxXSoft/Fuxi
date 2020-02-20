package mdu

import chisel3._
import chisel3.util._

import consts.Parameters._
import consts.MduOp.MDU_OP_WIDTH

class MDU extends Module {
  val io = IO(new Bundle {
    // control signals
    val flush   = Input(Bool())
    val op      = Input(UInt(MDU_OP_WIDTH.W))
    val valid   = Output(Bool())
    // data
    val opr1    = Input(UInt(DATA_WIDTH.W))
    val opr2    = Input(UInt(DATA_WIDTH.W))
    val result  = Output(UInt(DATA_WIDTH.W))
  })

  // decode operation to divider & multiplier control signals
  val mulEn :: divEn :: hiRem :: lhsSigned :: rhsSigned :: Nil =
      ListLookup(io.op, MduDecode.DEFAULT, MduDecode.TABLE)

  // operands
  val isOpr1Neg = lhsSigned && io.opr1(DATA_WIDTH - 1)
  val isOpr2Neg = rhsSigned && io.opr2(DATA_WIDTH - 1)
  val isAnsNeg  = isOpr1Neg ^ isOpr2Neg
  val opr1      = Mux(isOpr1Neg, -io.opr1, io.opr1)
  val opr2      = Mux(isOpr2Neg, -io.opr2, io.opr2)

  // multiplier
  val mul       = Module(new Multiplier(DATA_WIDTH, 1))
  val mulOut    = Mux(isAnsNeg, -mul.io.result, mul.io.result)
  val mulAns    = Mux(hiRem, mulOut(DATA_WIDTH * 2 - 1, DATA_WIDTH),
                             mulOut(DATA_WIDTH - 1, 0))
  mul.io.en    := mulEn
  mul.io.flush := io.flush || (mulEn && mul.io.done)
  mul.io.opr1  := opr1
  mul.io.opr2  := opr2

  // divider
  val div           = Module(new Divider(DATA_WIDTH))
  val allOnes       = Fill(DATA_WIDTH, 1.U)
  val oprMin        = 1.U << DATA_WIDTH
  val isDivOverflow = lhsSigned && opr1 === oprMin && opr2 === allOnes
  val isRemNeg      = lhsSigned && (io.opr1(DATA_WIDTH - 1) ^
                                    div.io.remainder(DATA_WIDTH - 1))
  val divQuo        = Mux(isAnsNeg, -div.io.quotient, div.io.quotient)
  val divAnsQuo     = Mux(div.io.divZero, allOnes,
                      Mux(isDivOverflow, oprMin, divQuo))
  val divRem        = Mux(isRemNeg, -div.io.remainder, div.io.remainder)
  val divAnsRem     = Mux(div.io.divZero, io.opr1,
                      Mux(isDivOverflow, 0.U, divRem))
  val divAns        = Mux(hiRem, divAnsRem, divAnsQuo)
  div.io.en        := divEn
  div.io.flush     := io.flush
  div.io.divident  := opr1
  div.io.divisor   := opr2

  // output signals
  io.valid  := Mux(mulEn, mul.io.done, Mux(divEn, div.io.done, true.B))
  io.result := Mux(mulEn, mulAns, Mux(divEn, divAns, 0.U))
}
