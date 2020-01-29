package core

import chisel3._
import chisel3.util.MuxLookup

import io._
import consts.AluOp._
import consts.MduOp.MDU_NOP
import consts.LsuOp.LSU_NOP
import mdu.MDU

class ALU extends Module {
  val io = IO(new Bundle {
    // from decoder
    val decoder   = Input(new DecoderIO)
    // MDU interface
    val mduFlush  = Input(Bool())
    val mduBusy   = Output(Bool())
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
    ALU_SLT   -> (opr1.asSInt < opr2.asSInt).asUInt,
    ALU_SLTU  -> (opr1 < opr2),
    ALU_SLL   -> (opr1 << shamt),
    ALU_SRL   -> (opr1 >> shamt),
    ALU_SRA   -> (opr1.asSInt >> shamt).asUInt,
  ))

  // multiplication & division unit
  val mdu       = Module(new MDU)
  mdu.io.flush  := io.mduFlush
  mdu.io.op     := io.decoder.mduOp
  io.mduBusy    := !mdu.io.valid
  mdu.io.opr1   := opr1.asUInt
  mdu.io.opr2   := opr2.asUInt
  val mduResult = Mux(mdu.io.valid, mdu.io.result, 0.U)

  // commit to write back
  val result = Mux(io.decoder.mduOp === MDU_NOP, aluResult, mduResult)
  val load   = io.decoder.lsuOp =/= LSU_NOP && io.decoder.regWen

  // signals to next stage
  io.alu.lsuOp        := io.decoder.lsuOp
  io.alu.lsuData      := io.decoder.lsuData
  io.alu.commit.en    := io.decoder.regWen
  io.alu.commit.addr  := io.decoder.regWaddr
  io.alu.commit.data  := result
  io.alu.commit.load  := load
  io.alu.csrOp        := io.decoder.csrOp
  io.alu.csrAddr      := io.decoder.csrAddr
  io.alu.csrData      := io.decoder.csrData
  io.alu.excType      := io.decoder.excType
  io.alu.inst         := io.decoder.inst
  io.alu.currentPc    := io.decoder.currentPc
}
