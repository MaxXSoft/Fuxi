package io

import chisel3._

import consts.Parameters._
import consts.AluOp.ALU_OP_WIDTH
import consts.MduOp.MDU_OP_WIDTH
import consts.LsuOp.LSU_OP_WIDTH
import consts.CsrOp.CSR_OP_WIDTH
import consts.CSR.CSR_ADDR_WIDTH
import consts.ExceptType.EXC_TYPE_WIDTH
import lsu.LsuDecode.LS_DATA_WIDTH

// interface of stage's IO
class StageIO[T <: StageIO[T]] extends Bundle {
  this: T =>

  // for initializing flip-flops in mid-stage
  def default() = 0.U.asTypeOf(this)
}

// IF stage
class FetchIO extends StageIO[FetchIO] {
  import consts.Instructions.NOP

  val inst      = UInt(INST_WIDTH.W)
  val pc        = UInt(ADDR_WIDTH.W)
  val predIndex = UInt(ADDR_WIDTH.W)
  val pageFault = Bool()

  override def default() = {
    val init = Wire(new FetchIO)
    init.inst := NOP
    init.pc := 0.U
    init.predIndex := 0.U
    init.pageFault := false.B
    init
  }
}

// ID stage
class DecoderIO extends StageIO[DecoderIO] {
  // to ALU/MDU
  val aluOp     = UInt(ALU_OP_WIDTH.W)
  val opr1      = UInt(DATA_WIDTH.W)
  val opr2      = UInt(DATA_WIDTH.W)
  val mduOp     = UInt(MDU_OP_WIDTH.W)
  // to Mem (LSU)
  val lsuOp     = UInt(LSU_OP_WIDTH.W)
  val lsuData   = UInt(DATA_WIDTH.W)
  // to write back
  val regWen    = Bool()
  val regWaddr  = UInt(REG_ADDR_WIDTH.W)
  // to CSR
  val csrOp     = UInt(CSR_OP_WIDTH.W)
  val csrAddr   = UInt(CSR_ADDR_WIDTH.W)
  val csrData   = UInt(DATA_WIDTH.W)
  // exception type
  val excType   = UInt(EXC_TYPE_WIDTH.W)
  // instruction info
  val inst      = UInt(INST_WIDTH.W)
  val currentPc = UInt(ADDR_WIDTH.W)
}

// EX stage
class AluIO extends StageIO[AluIO] {
  // to Mem (LSU)
  val lsuOp     = UInt(LSU_OP_WIDTH.W)
  val lsuData   = UInt(DATA_WIDTH.W)
  // to write back
  val reg       = new RegCommitIO
  // to CSR
  val csr       = new CsrCommitIO
  // exception type
  val excType   = UInt(EXC_TYPE_WIDTH.W)
  // instruction info
  val inst      = UInt(INST_WIDTH.W)
  val currentPc = UInt(ADDR_WIDTH.W)
}

// MEM stage
class MemIO extends StageIO[MemIO] {
  // to write back
  val reg       = new RegCommitIO
  val memSigned = Bool()
  val memSel    = UInt(ADDR_ALIGN_WIDTH.W)
  val memWidth  = UInt(LS_DATA_WIDTH.W)
  // to CSR
  val csr       = new CsrCommitIO
  // debug
  val currentPc = UInt(ADDR_WIDTH.W)
}
