package bpu

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.HasCoreParams
import chisel3.util.Cat

import consts.Architecture._

// single line in BTB
class BtbLine(implicit val p: Parameters) extends Bundle with HasCoreParams {
  val jump    = Bool()
  val pc      = UInt(BTB_TAG_BITS.W)
  val target  = UInt((ADDR_WIDTH - INST_ALIGN_WIDTH).W)
}

// branch target buffer
class BTB(implicit val p: Parameters) extends Module with HasCoreParams {
  val io = IO(new Bundle {
    // branch information (from decoder)
    val branch        = Input(Bool())
    val jump          = Input(Bool())
    val pc            = Input(UInt(ADDR_WIDTH.W))
    val target        = Input(UInt(ADDR_WIDTH.W))
    // BTB lookup interface
    val lookupPc      = Input(UInt(ADDR_WIDTH.W))
    val lookupBranch  = Output(Bool())
    val lookupJump    = Output(Bool())
    val lookupTarget  = Output(UInt(ADDR_WIDTH.W))
    // Speculative candidate, valid only when lookupBranch is true.
    val lookupTargetCandidate = Output(UInt(ADDR_WIDTH.W))
  })

  // definitions of BTB lines and valid bits
  val valids  = RegInit(VecInit(Seq.fill(BTB_ENTRIES) { false.B }))
  val lines   = Mem(BTB_ENTRIES, new BtbLine)

  // branch info for BTB lines
  val index   = io.pc(BTB_INDEX_BITS + INST_ALIGN_WIDTH - 1,
                      INST_ALIGN_WIDTH)
  val linePc  = io.pc(ADDR_WIDTH - 1, BTB_INDEX_BITS + INST_ALIGN_WIDTH)

  // write to BTB lines
  // Use one whole-line write and one shared asynchronous read. Repeated
  // field accesses create separate memory ports and can prevent LUT RAM
  // inference even though every field has the same address and enable.
  val writeLine = Wire(new BtbLine)
  writeLine.jump := io.jump
  writeLine.pc := linePc
  writeLine.target := io.target(ADDR_WIDTH - 1, INST_ALIGN_WIDTH)
  when (io.branch) {
    valids(index) := true.B
    lines(index) := writeLine
  }

  // signals about BTB lookup
  val lookupIndex = io.lookupPc(BTB_INDEX_BITS + INST_ALIGN_WIDTH - 1,
                                INST_ALIGN_WIDTH)
  val lookupPcSel = io.lookupPc(ADDR_WIDTH - 1,
                                BTB_INDEX_BITS + INST_ALIGN_WIDTH)
  val readLine    = lines(lookupIndex)
  val btbHit      = valids(lookupIndex) &&
                    readLine.pc === lookupPcSel

  // BTB lookup
  io.lookupBranch := btbHit
  io.lookupJump   := Mux(btbHit, readLine.jump, false.B)
  io.lookupTarget := Cat(Mux(btbHit, readLine.target, 0.U),
                         0.U(INST_ALIGN_WIDTH.W))
  io.lookupTargetCandidate := Cat(readLine.target, 0.U(INST_ALIGN_WIDTH.W))
}
