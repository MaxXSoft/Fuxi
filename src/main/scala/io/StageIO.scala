package io

import chisel3._

import consts.Parameters._
import consts.AluOp
import consts.MduOp
import consts.LsuOp
import consts.CsrOp
import consts.CSR.CSR_ADDR_WIDTH
import consts.ExceptType
import lsu.LsuWidth

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
  val inst      = UInt(INST_WIDTH.W)
  // branch prediction result
  val taken     = Bool()
  val target    = UInt(ADDR_WIDTH.W)
  val predIndex = UInt(GHR_WIDTH.W)
  // instruction fetch page fault
  val pageFault = Bool()
  // instruction fetch physical access fault
  val accessFault = Bool()
  val faultAddr = UInt(ADDR_WIDTH.W)
  // performance monitoring
  val pmu = new PredictionPerformanceIO
}

// ID stage
class DecoderIO extends StageIO {
  // to ALU/MDU
  val aluOp     = AluOp()
  val opr1      = UInt(DATA_WIDTH.W)
  val opr2      = UInt(DATA_WIDTH.W)
  val mduOp     = MduOp()
  // to Mem (LSU)
  val lsuOp     = LsuOp()
  val lsuData   = UInt(DATA_WIDTH.W)
  // to write back
  val regWen    = Bool()
  val regWaddr  = UInt(REG_ADDR_WIDTH.W)
  // to CSR
  val csrOp     = CsrOp()
  val csrAddr   = UInt(CSR_ADDR_WIDTH.W)
  val csrData   = UInt(DATA_WIDTH.W)
  // exception information
  val excType   = ExceptType()
  val excValue  = UInt(DATA_WIDTH.W)
  // instruction info
  val valid     = Bool()
  val inst      = UInt(INST_WIDTH.W)
  val currentPc = UInt(ADDR_WIDTH.W)
  // performance monitoring
  val pmu = new RetirePerformanceIO
}

// EX stage
class AluIO extends StageIO {
  // to Mem (LSU)
  val lsuOp     = LsuOp()
  val lsuData   = UInt(DATA_WIDTH.W)
  // to write back
  val reg       = new RegCommitIO
  // to CSR
  val csr       = new CsrCommitIO
  // exception information
  val excType   = ExceptType()
  val excValue  = UInt(DATA_WIDTH.W)
  // instruction info
  val valid     = Bool()
  val inst      = UInt(INST_WIDTH.W)
  val currentPc = UInt(ADDR_WIDTH.W)
  // performance monitoring
  val pmu = new RetirePerformanceIO
}

// MEM stage
class MemIO extends StageIO {
  // to write back
  val reg       = new RegCommitIO
  val memSigned = Bool()
  val memSel    = UInt(ADDR_ALIGN_WIDTH.W)
  val memWidth  = LsuWidth()
  // to CSR
  val csr       = new CsrCommitIO
  // to exclusive monitor
  val excMon    = new ExcMonCommitIO
  // debug
  val currentPc = UInt(ADDR_WIDTH.W)
  // performance monitoring
  val pmu = new RetirePerformanceIO
}
