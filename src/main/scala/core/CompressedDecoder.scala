package core

import chisel3._
import chisel3.util._

class CompressedDecoder extends Module {
  val io = IO(new Bundle {
    val inst         = Input(UInt(32.W))
    val expanded     = Output(UInt(32.W))
    val isCompressed = Output(Bool())
    val illegal      = Output(Bool())
  })

  val inst = io.inst(15, 0)
  val rd = inst(11, 7)
  val rs2 = inst(6, 2)
  val compactRd = Cat(1.U(2.W), inst(4, 2))
  val compactRs1 = Cat(1.U(2.W), inst(9, 7))
  val zero = 0.U(5.W)
  val sp = 2.U(5.W)

  def iType(imm: UInt, rs1: UInt, funct3: Int, dest: UInt,
            opcode: Int = 0x13): UInt =
    Cat(imm(11, 0), rs1, funct3.U(3.W), dest, opcode.U(7.W))

  def rType(funct7: Int, source2: UInt, source1: UInt,
            funct3: Int, dest: UInt): UInt =
    Cat(funct7.U(7.W), source2, source1, funct3.U(3.W), dest, 0x33.U(7.W))

  def store(imm: UInt, source2: UInt, source1: UInt): UInt =
    Cat(imm(11, 5), source2, source1, 2.U(3.W), imm(4, 0), 0x23.U(7.W))

  def branch(imm: UInt, source1: UInt, funct3: UInt): UInt =
    Cat(imm(12), imm(10, 5), zero, source1, funct3,
        imm(4, 1), imm(11), 0x63.U(7.W))

  def jump(imm: UInt, dest: UInt): UInt =
    Cat(imm(20), imm(10, 1), imm(11), imm(19, 12), dest, 0x6f.U(7.W))

  val ciImm = Cat(Fill(7, inst(12)), inst(6, 2))
  val addi4spnImm = Cat(0.U(2.W), inst(10, 7), inst(12, 11),
                        inst(5), inst(6), 0.U(2.W))
  val wordImm = Cat(0.U(5.W), inst(5), inst(12, 10), inst(6), 0.U(2.W))
  val addi16spImm = Cat(Fill(3, inst(12)), inst(4, 3), inst(5),
                        inst(2), inst(6), 0.U(4.W))
  val jumpImm = Cat(Fill(10, inst(12)), inst(8), inst(10, 9), inst(6),
                    inst(7), inst(2), inst(11), inst(5, 3), 0.U(1.W))
  val branchImm = Cat(Fill(5, inst(12)), inst(6, 5), inst(2),
                      inst(11, 10), inst(4, 3), 0.U(1.W))
  val lwspImm = Cat(0.U(4.W), inst(3, 2), inst(12), inst(6, 4), 0.U(2.W))
  val swspImm = Cat(0.U(4.W), inst(8, 7), inst(12, 9), 0.U(2.W))
  val shamt = Cat(0.U(7.W), inst(6, 2))

  val expanded = WireDefault(0.U(32.W))
  val legal = WireDefault(false.B)
  val hint = WireDefault(false.B)

  switch (inst(1, 0)) {
    is (0.U) {
      switch (inst(15, 13)) {
        is (0.U) { // C.ADDI4SPN
          expanded := iType(addi4spnImm, sp, 0, compactRd)
          legal := addi4spnImm =/= 0.U
        }
        is (2.U) { // C.LW
          expanded := iType(wordImm, compactRs1, 2, compactRd, 0x03)
          legal := true.B
        }
        is (6.U) { // C.SW
          expanded := store(wordImm, compactRd, compactRs1)
          legal := true.B
        }
      }
    }
    is (1.U) {
      switch (inst(15, 13)) {
        is (0.U) { // C.ADDI / C.NOP
          expanded := iType(ciImm, rd, 0, rd)
          legal := true.B
          hint := rd === 0.U || ciImm === 0.U
        }
        is (1.U) { // C.JAL (RV32)
          expanded := jump(jumpImm, 1.U(5.W))
          legal := true.B
        }
        is (2.U) { // C.LI
          expanded := iType(ciImm, zero, 0, rd)
          legal := true.B
          hint := rd === 0.U
        }
        is (3.U) { // C.ADDI16SP / C.LUI
          when(rd === 2.U) {
            expanded := iType(addi16spImm, sp, 0, sp)
            legal := addi16spImm =/= 0.U
          }.otherwise {
            expanded := Cat(Fill(15, inst(12)), inst(6, 2), rd, 0x37.U(7.W))
            legal := ciImm =/= 0.U
            hint := rd === 0.U
          }
        }
        is (4.U) {
          switch (inst(11, 10)) {
            is (0.U) { // C.SRLI
              expanded := iType(shamt, compactRs1, 5, compactRs1)
              legal := !inst(12)
              hint := shamt === 0.U
            }
            is (1.U) { // C.SRAI
              expanded := iType(shamt | 0x400.U(12.W), compactRs1, 5, compactRs1)
              legal := !inst(12)
              hint := shamt === 0.U
            }
            is (2.U) { // C.ANDI
              expanded := iType(ciImm, compactRs1, 7, compactRs1)
              legal := true.B
            }
            is (3.U) {
              legal := !inst(12) // RV64 word operations and Zc encodings are unsupported.
              switch (inst(6, 5)) {
                is (0.U) { expanded := rType(0x20, compactRd, compactRs1, 0, compactRs1) }
                is (1.U) { expanded := rType(0, compactRd, compactRs1, 4, compactRs1) }
                is (2.U) { expanded := rType(0, compactRd, compactRs1, 6, compactRs1) }
                is (3.U) { expanded := rType(0, compactRd, compactRs1, 7, compactRs1) }
              }
            }
          }
        }
        is (5.U) { // C.J
          expanded := jump(jumpImm, zero)
          legal := true.B
        }
        is (6.U, 7.U) { // C.BEQZ / C.BNEZ
          expanded := branch(branchImm, compactRs1, Cat(0.U(2.W), inst(13)))
          legal := true.B
        }
      }
    }
    is (2.U) {
      switch (inst(15, 13)) {
        is (0.U) { // C.SLLI
          expanded := iType(shamt, rd, 1, rd)
          legal := !inst(12)
          hint := rd === 0.U || shamt === 0.U
        }
        is (2.U) { // C.LWSP
          expanded := iType(lwspImm, sp, 2, rd, 0x03)
          legal := rd =/= 0.U
        }
        is (4.U) {
          when (!inst(12)) {
            when (rs2 === 0.U) { // C.JR
              expanded := iType(0.U(12.W), rd, 0, zero, 0x67)
              legal := rd =/= 0.U
            } .otherwise { // C.MV
              expanded := rType(0, rs2, zero, 0, rd)
              legal := true.B
              hint := rd === 0.U
            }
          } .otherwise {
            legal := true.B
            when (rs2 === 0.U) {
              when (rd === 0.U) { // C.EBREAK
                expanded := 0x00100073.U(32.W)
              } .otherwise { // C.JALR
                expanded := iType(0.U(12.W), rd, 0, 1.U(5.W), 0x67)
              }
            } .otherwise { // C.ADD
              expanded := rType(0, rs2, rd, 0, rd)
              hint := rd === 0.U
            }
          }
        }
        is (6.U) { // C.SWSP
          expanded := store(swspImm, rs2, sp)
          legal := true.B
        }
      }
    }
  }

  io.isCompressed := inst(1, 0) =/= 3.U
  io.illegal := io.isCompressed && !legal
  io.expanded := Mux(!io.isCompressed, io.inst,
                 Mux(!legal, 0.U, Mux(hint, 0x00000013.U, expanded)))
}
