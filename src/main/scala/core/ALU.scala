package core

import chisel3._
import chisel3.util.MuxLookup

import io._
import consts.AluOp._
import consts.MduOp.MDU_NOP
import mdu.MDU

class ALU extends Module {
  val io = IO(new Bundle {
    // from decoder
    val decoder   = Input(new DecoderIO)
    // MDU interface
    val mduFlush  = Input(Bool())
    val mduBusy   = Output(Bool())
    val mdu       = Flipped(new MduIO)
    // to next stage
    val alu       = Output(new AluIO)
  })

  // operands
  val opr1  = io.decoder.opr1
  val opr2  = io.decoder.opr2
  val shamt = opr2(4, 0)

  // result of ALU
  val aluResult = MuxLookup(io.decoder.aluOp, 0.U, Seq(
    ALU_ADD   -> (opr1 + opr2),
    ALU_SUB   -> (opr1 - opr2),
    ALU_XOR   -> (opr1 ^ opr2),
    ALU_OR    -> (opr1 | opr2),
    ALU_AND   -> (opr1 & opr2),
    ALU_SLT   -> (opr1 < opr2),
    ALU_SLTU  -> (opr1.asUInt < opr2.asUInt),
    ALU_SLL   -> (opr1 << shamt),
    ALU_SRL   -> (opr1.asUInt >> shamt),
    ALU_SRA   -> (opr1 >> shamt),
  ))

  // multiplication & division unit
  val mdu       = new MDU
  io.mdu        <> mdu.io
  io.mdu.flush  := io.mduFlush
  io.mduBusy    := !io.mdu.valid
  val mduResult = Mux(io.mdu.valid, io.mdu.result, 0.U)

  // final result
  val result = Mux(io.decoder.mduOp === MDU_NOP, aluResult, mduResult)

  // signals to next stage
  io.alu.lsuOp      := io.decoder.lsuOp
  io.alu.lsuData    := io.decoder.lsuData
  io.alu.regWen     := io.decoder.regWen
  io.alu.regWaddr   := io.decoder.regWaddr
  io.alu.result     := result
  io.alu.csrOp      := io.decoder.csrOp
  io.alu.csrAddr    := io.decoder.csrAddr
  io.alu.csrData    := io.decoder.csrData
  io.alu.excType    := io.decoder.excType
  io.alu.currentPc  := io.decoder.currentPc
}
