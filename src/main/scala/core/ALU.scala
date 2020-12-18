package core

import chisel3._
import chisel3.util.MuxLookup

import io._
import consts.AluOp._
import consts.MduOp.MDU_NOP
import consts.LsuOp.LSU_NOP
import consts.CsrOp.CSR_NOP
import consts.ExceptType.EXC_ILLEG
import consts.Instructions.NOP
import mdu.MDU

class ALU extends Module {
  val io = IO(new Bundle {
    // from decoder
    val decoder   = Input(new DecoderIO)
    // pipeline control
    val flush     = Input(Bool())
    val stallReq  = Output(Bool())
    // CSR read channel
    val csrRead   = new CsrReadIO
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
  mdu.io.flush  := io.flush
  mdu.io.op     := io.decoder.mduOp
  mdu.io.opr1   := opr1.asUInt
  mdu.io.opr2   := opr2.asUInt
  val mduResult = Mux(mdu.io.valid, mdu.io.result, 0.U)

  // CSR control & exception type
  val csrEn   = io.decoder.csrOp =/= CSR_NOP
  val retired = io.decoder.inst =/= NOP
  val excType = Mux(csrEn && !io.csrRead.valid,
                    EXC_ILLEG, io.decoder.excType)

  // commit to write back
  val result  = Mux(csrEn, io.csrRead.data,
                Mux(io.decoder.mduOp =/= MDU_NOP, mduResult, aluResult))
  val load    = io.decoder.lsuOp =/= LSU_NOP && io.decoder.regWen

  // pipeline control signals
  io.stallReq := !mdu.io.valid

  // read data from CSR
  io.csrRead.op   := io.decoder.csrOp
  io.csrRead.addr := io.decoder.csrAddr

  // signals to next stage
  io.alu.lsuOp        := io.decoder.lsuOp
  io.alu.lsuData      := io.decoder.lsuData
  io.alu.reg.en       := io.decoder.regWen
  io.alu.reg.addr     := io.decoder.regWaddr
  io.alu.reg.data     := result
  io.alu.reg.load     := load
  io.alu.csr.op       := io.decoder.csrOp
  io.alu.csr.addr     := io.decoder.csrAddr
  io.alu.csr.data     := io.decoder.csrData
  io.alu.csr.retired  := retired
  io.alu.excType      := excType
  io.alu.excValue     := io.decoder.excValue
  io.alu.valid        := io.decoder.valid
  io.alu.inst         := io.decoder.inst
  io.alu.currentPc    := io.decoder.currentPc
}
