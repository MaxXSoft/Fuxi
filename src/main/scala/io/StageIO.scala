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
class StageIO extends Bundle {
  // for initializing flip-flops in mid-stage
  def default() = 0.U.asTypeOf(this)
}

// IF stage
class FetchIO extends StageIO {
  // instruction info
  val valid     = Bool()
  val pc        = UInt(ADDR_WIDTH.W)
  // branch prediction result
  val taken     = Bool()
  val target    = UInt(ADDR_WIDTH.W)
  val predIndex = UInt(GHR_WIDTH.W)
  // instruction fetch page fault
  val pageFault = Bool()
}

// ID stage
class DecoderIO extends StageIO {
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
  // exception information
  val excType   = UInt(EXC_TYPE_WIDTH.W)
  val excValue  = UInt(DATA_WIDTH.W)
  // instruction info
  val valid     = Bool()
  val inst      = UInt(INST_WIDTH.W)
  val currentPc = UInt(ADDR_WIDTH.W)
}

// EX stage
class AluIO extends StageIO {
  // to Mem (LSU)
  val lsuOp     = UInt(LSU_OP_WIDTH.W)
  val lsuData   = UInt(DATA_WIDTH.W)
  // to write back
  val reg       = new RegCommitIO
  // to CSR
  val csr       = new CsrCommitIO
  // exception information
  val excType   = UInt(EXC_TYPE_WIDTH.W)
  val excValue  = UInt(DATA_WIDTH.W)
  // instruction info
  val valid     = Bool()
  val inst      = UInt(INST_WIDTH.W)
  val currentPc = UInt(ADDR_WIDTH.W)
}

// MEM stage
class MemIO extends StageIO {
  // to write back
  val reg       = new RegCommitIO
  val memSigned = Bool()
  val memSel    = UInt(ADDR_ALIGN_WIDTH.W)
  val memWidth  = UInt(LS_DATA_WIDTH.W)
  // to CSR
  val csr       = new CsrCommitIO
  // to exclusive monitor
  val excMon    = new ExcMonCommitIO
  // debug
  val currentPc = UInt(ADDR_WIDTH.W)
}
