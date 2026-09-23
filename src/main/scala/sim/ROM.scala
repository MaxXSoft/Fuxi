package sim

import chisel3._
import chisel3.util.experimental.loadMemoryFromFile

import io.SramIO
import consts.Parameters._
import consts.Instructions.NOP

object ROM {
  val DEPTH = 256

  sealed trait Init
  case class File(path: String) extends Init
  case class Words(values: Seq[BigInt]) extends Init {
    require(values.nonEmpty && values.size <= DEPTH, s"ROM requires 1 to $DEPTH words")
    require(values.forall(w => w >= 0 && w < (BigInt(1) << INST_WIDTH)),
      s"ROM words must be unsigned $INST_WIDTH-bit values")
  }
}

// for simulation only
class ROM(init: ROM.Init) extends Module {
  def this(initFile: String) = this(ROM.File(initFile))
  def this(words: Seq[BigInt]) = this(ROM.Words(words))
  val io = IO(Flipped(new SramIO(ADDR_WIDTH, INST_WIDTH)))

  val data  = RegInit(NOP)
  val addr  = io.addr - RESET_PC
  val index = addr(ADDR_ALIGN_WIDTH + chisel3.util.log2Ceil(ROM.DEPTH) - 1, ADDR_ALIGN_WIDTH)
  val readData = init match {
    case ROM.File(path) =>
      val rom = Mem(ROM.DEPTH, UInt(INST_WIDTH.W))
      loadMemoryFromFile(rom, path)
      rom(index)
    case ROM.Words(words) =>
      val rom = VecInit(words.padTo(ROM.DEPTH, NOP.litValue).map(_.U(INST_WIDTH.W)))
      rom(index)
  }

  when (io.en) {
    data := readData
  } .otherwise {
    data := 0.U
  }

  io.valid := true.B
  io.fault := false.B
  io.accessFault := false.B
  io.rdata := data
}
