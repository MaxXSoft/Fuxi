package utils

import chisel3._

import io._
import consts.CsrOp.{CSR_NOP, CSR_R, CSR_W}

class HazardResolver extends Module {
  val io = IO(new Bundle {
    // regfile read channel from decoder
    val regRead1  = Flipped(new RegReadIO)
    val regRead2  = Flipped(new RegReadIO)
    // CSR read channel from ALU
    val csrRead   = Flipped(new CsrReadIO)
    // exclusive monitor check channel from memReg stage
    val check     = Flipped(new ExcMonCheckIO)
    // commit channel from ALU
    val aluReg    = Input(new RegCommitIO)
    // commit channel from mem stage
    val memReg    = Input(new RegCommitIO)
    val memCsr    = Input(new CsrCommitIO)
    // commit channel from write back
    val wbReg     = Input(new RegCommitIO)
    val wbCsr     = Input(new CsrCommitIO)
    val wbExcMon  = Input(new ExcMonCommitIO)
    // read channel of regfile
    val rf1       = new RegReadIO
    val rf2       = new RegReadIO
    // read channel of CSR
    val csr       = new CsrReadIO
    // check channel of exclusive monitor
    val excMon    = new ExcMonCheckIO
    // hazard flags
    val loadFlag  = Output(Bool())
    val csrFlag   = Output(Bool())
  })

  def forwardReg(read: RegReadIO, rf: RegReadIO) = {
    when (read.en && read.addr =/= 0.U) {
      when (io.aluReg.en && read.addr === io.aluReg.addr) {
        read.data := io.aluReg.data
      } .elsewhen (io.memReg.en && read.addr === io.memReg.addr) {
        read.data := io.memReg.data
      } .elsewhen (io.wbReg.en && read.addr === io.wbReg.addr) {
        read.data := io.wbReg.data
      } .otherwise {
        read.data := rf.data
      }
    } .otherwise {
      read.data := 0.U
    }
  }

  def forwardExcMon(check: ExcMonCheckIO, excMon: ExcMonCheckIO) = {
    when (io.wbExcMon.clear || io.wbExcMon.set) {
      when (check.addr === io.wbExcMon.addr) {
        when (io.wbExcMon.clear) {
          check.valid := false.B
        } .otherwise {
          check.valid := true.B
        }
      } .otherwise {
        check.valid := false.B
      }
    } .otherwise {
      check.valid := excMon.valid
    }
  }

  def resolveLoadHazard(read: RegReadIO) = {
    val aluLoad = io.aluReg.load && read.addr === io.aluReg.addr
    val memLoad = io.memReg.load && read.addr === io.memReg.addr
    read.en && (aluLoad || memLoad)
  }

  def resolveCsrHazard(read: CsrReadIO) = {
    val isRead  = read.op =/= CSR_NOP && read.op =/= CSR_W
    val memCsr  = io.memCsr.op =/= CSR_NOP && io.memCsr.op =/= CSR_R &&
                  read.addr === io.memCsr.addr
    val wbCsr   = io.wbCsr.op =/= CSR_NOP && io.wbCsr.op =/= CSR_R &&
                  read.addr === io.wbCsr.addr
    isRead && (memCsr || wbCsr)
  }

  // forward regfile read channels
  forwardReg(io.regRead1, io.rf1)
  forwardReg(io.regRead2, io.rf2)

  // forward exclusive monitor check channel
  forwardExcMon(io.check, io.excMon)

  // hazard flags
  val loadHazard1 = resolveLoadHazard(io.regRead1)
  val loadHazard2 = resolveLoadHazard(io.regRead2)
  val csrHazard   = resolveCsrHazard(io.csrRead)

  // regfile read signals
  io.rf1.en   := io.regRead1.en
  io.rf1.addr := io.regRead1.addr
  io.rf2.en   := io.regRead2.en
  io.rf2.addr := io.regRead2.addr

  // CSR read signals
  io.csr <> io.csrRead

  // exclusive monitor check signals
  io.excMon.addr  := io.check.addr

  // hazard flags
  io.loadFlag := loadHazard1 || loadHazard2
  io.csrFlag  := csrHazard
}
