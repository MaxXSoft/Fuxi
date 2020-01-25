package core

import chisel3._
import chisel3.util._

import io._
import consts.Control._
import consts.ExceptType._
import consts.LsuOp.LSU_NOP
import consts.CsrOp.CSR_NOP

class Decoder extends Module {
  val io = IO(new Bundle {
    // from fetch stage
    val fetch   = Input(new FetchIO)
    // regfile read channels
    val read1   = new RegReadIO
    val read2   = new RegReadIO
    // branch information
    val branch  = Output(new BranchInfoIO)
    // to next stage
    val decoder = Output(new DecoderIO)
  })

  val inst  = io.fetch.inst

  // regfile addresses
  val rd  = inst(11, 7)
  val rs1 = inst(19, 15)
  val rs2 = inst(24, 20)

  // immediate
  val immI  = inst(31, 20).asSInt
  val immS  = Cat(inst(31, 25), inst(11, 7)).asSInt
  val immB  = Cat(inst(31), inst(7), inst(30, 25),
                  inst(11, 8), 0.U(1.W)).asSInt
  val immU  = Cat(inst(31, 12), 0.U(12.W)).asSInt
  val immJ  = Cat(inst(31), inst(19, 12), inst(20),
                  inst(30, 21), 0.U(1.W)).asSInt

  // operand generator
  def generateOpr(oprSel: UInt) =
      MuxLookup(oprSel, 0.S, Seq(
        OPR_REG1  -> io.read1.data.asSInt,
        OPR_REG2  -> io.read2.data.asSInt,
        OPR_IMMI  -> immI,
        OPR_IMMS  -> immS,
        OPR_IMMU  -> immU,
        OPR_IMMR  -> rs2.zext,
        OPR_PC    -> io.fetch.pc.asSInt,
        OPR_4     -> 4.S,
      ))

  // control signals
  val ((regEn1: Bool) :: (regEn2: Bool) :: (regWen: Bool) ::
       aluSrc1 :: aluSrc2 :: aluOp :: branchFlag :: lsuOp :: csrOp ::
       mduOp :: excType :: Nil) = ListLookup(io.fetch.inst, DEFAULT, TABLE)
  
  // branch taken/target
  val targetJAL     = (io.fetch.pc.asSInt + immJ).asUInt
  val targetJALR    = Cat((io.read1.data.asSInt + immI)(31, 1), 0.U)
  val targetB       = (io.fetch.pc.asSInt + immB).asUInt
  val branchTaken   = MuxLookup(branchFlag, false.B, Seq(
    BR_AL   -> true.B,
    BR_EQ   -> (io.read1.data === io.read2.data),
    BR_NE   -> (io.read1.data =/= io.read2.data),
    BR_LT   -> (io.read1.data.asSInt < io.read2.data.asSInt),
    BR_GE   -> (io.read1.data.asSInt >= io.read2.data.asSInt),
    BR_LTU  -> (io.read1.data < io.read2.data),
    BR_GEU  -> (io.read1.data >= io.read2.data),
  ))
  val branchTarget  = Mux(branchFlag === BR_N, 0.U,
                      Mux(branchFlag === BR_AL,
                          Mux(regEn1, targetJALR, targetJAL),
                          targetB))

  // CSR related signals
  val csrData = Mux(csrOp === CSR_NOP, 0.U,
                Mux(regEn1, io.read1.data, rs1))

  // LSU related signals
  // cancel all LSU operations after fetching illegal instructions
  val lsuOperation = Mux(excType === EXC_ILLEG, LSU_NOP, lsuOp)

  // exception signal
  val exceptType = Mux(io.fetch.pageFault, EXC_IPAGE, excType)

  // regfile read signals
  io.read1.en   := regEn1
  io.read1.addr := rs1
  io.read2.en   := regEn2
  io.read2.addr := rs2

  // branch information
  io.branch.branch  := branchFlag =/= BR_N
  io.branch.jump    := branchFlag === BR_AL
  io.branch.taken   := branchTaken
  io.branch.index   := io.fetch.predIndex
  io.branch.pc      := io.fetch.pc
  io.branch.target  := branchTarget

  // signals to next stage
  io.decoder.aluOp    := aluOp
  io.decoder.opr1     := generateOpr(aluSrc1)
  io.decoder.opr2     := generateOpr(aluSrc2)
  io.decoder.mduOp    := mduOp
  io.decoder.lsuOp    := lsuOperation
  io.decoder.lsuData  := io.read2.data
  io.decoder.regWen   := regWen
  io.decoder.regWaddr := rd
  io.decoder.csrOp    := csrOp
  io.decoder.csrAddr  := immI.asUInt
  io.decoder.csrData  := csrData
  io.decoder.excType  := exceptType
}
