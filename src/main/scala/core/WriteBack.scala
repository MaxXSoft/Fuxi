package core

import chisel3._
import chisel3.util.MuxLookup

import io._
import lsu.LsuDecode._
import consts.Parameters.{ADDR_WIDTH, DATA_WIDTH}

class WriteBack extends Module {
  val io = IO(new Bundle {
    // from mem stage
    val mem     = Input(new MemIO)
    // from RAM
    val ramData = Input(UInt(DATA_WIDTH.W))
    // commit to regfile
    val reg     = Output(new RegCommitIO)
    // to CSR
    val csr     = Output(new CsrCommitIO)
    // to exclusive monitor
    val excMon  = Output(new ExcMonCommitIO)
    // debug interface
    val debug   = new DebugIO
  })

  def mapRamData(i: Int, w: Int) = {
    val d = io.ramData((i + 1) * w - 1, i * w)
    val sext = Wire(SInt(DATA_WIDTH.W))
    sext := d.asSInt
    (i * w / 8).U -> Mux(io.mem.memSigned, sext.asUInt, d)
  }

  // data from RAM
  val byteSeq   = 0 until ADDR_WIDTH /  8 map { i => mapRamData(i,  8) }
  val halfSeq   = 0 until ADDR_WIDTH / 16 map { i => mapRamData(i, 16) }
  val wordSeq   = 0 until ADDR_WIDTH / 32 map { i => mapRamData(i, 32) }
  val loadData  = MuxLookup(io.mem.memWidth, 0.U, Seq(
    LS_DATA_BYTE  -> MuxLookup(io.mem.memSel, 0.U, byteSeq),
    LS_DATA_HALF  -> MuxLookup(io.mem.memSel, 0.U, halfSeq),
    LS_DATA_WORD  -> MuxLookup(io.mem.memSel, 0.U, wordSeq),
  ))

  // data that write back to regfile
  val regData = Mux(io.mem.reg.load, loadData, io.mem.reg.data)

  // output signals
  io.reg.en   := io.mem.reg.en
  io.reg.addr := io.mem.reg.addr
  io.reg.data := regData
  io.reg.load := false.B
  io.csr      <> io.mem.csr
  io.excMon   <> io.mem.excMon

  // debug signals
  io.debug.regWen   := io.mem.reg.en
  io.debug.regWaddr := io.mem.reg.addr
  io.debug.regWdata := regData
  io.debug.pc       := io.mem.currentPc
}
