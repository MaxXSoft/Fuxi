package core

import chisel3.Element
import chisel3.iotesters.{Driver, PeekPokeTester, Pokeable}

import consts.AluOp._
import consts.MduOp._
import consts.LsuOp._
import consts.CsrOp._
import consts.ExceptType._

class DecoderUnitTester(c: Decoder) extends PeekPokeTester(c) {
  val pc    = 0x00000200
  val reg1  = 0xabcdef01
  val reg1u = BigInt(reg1.toHexString, 16)
  val reg2  = 0x12345679

  def pokeDecoder(inst: Int) = {
    poke(c.io.fetch.valid, true)
    poke(c.io.fetch.pc, pc)
    poke(c.io.fetch.predIndex, 0)
    poke(c.io.fetch.pageFault, false)
    poke(c.io.inst, inst)
    poke(c.io.read1.data, reg1)
    poke(c.io.read2.data, reg2)
  }

  def expectBranch(branch: Boolean, jump: Boolean, taken: Boolean,
                   target: BigInt) = {
    expect(c.io.branch.branch, branch)
    expect(c.io.branch.jump, jump)
    expect(c.io.branch.taken, taken)
    expect(c.io.branch.pc, pc)
    if (branch) expect(c.io.branch.target, target)
  }

  def expectReg(w: Option[Int], r1: Option[Int], r2: Option[Int]) = {
    def single[T <: Element: Pokeable](v: Option[Int], en: T, addr: T) = {
      v match {
        case Some(a) => expect(en, true); expect(addr, a)
        case None => expect(en, false)
      }
    }
    single(w, c.io.decoder.regWen, c.io.decoder.regWaddr)
    single(r1, c.io.read1.en, c.io.read1.addr)
    single(r2, c.io.read2.en, c.io.read2.addr)
  }

  def expectAlu(aluOp: BigInt, mduOp: BigInt, opr1: Int, opr2: Int) = {
    expect(c.io.decoder.aluOp, aluOp)
    expect(c.io.decoder.opr1, BigInt(opr1.toHexString, 16))
    expect(c.io.decoder.opr2, BigInt(opr2.toHexString, 16))
    expect(c.io.decoder.mduOp, mduOp)
  }

  def expectLsu(lsuOp: BigInt, data: BigInt) = {
    expect(c.io.decoder.lsuOp, lsuOp)
    if (lsuOp != LSU_NOP.litValue && lsuOp != LSU_LB.litValue &&
        lsuOp != LSU_LH.litValue && lsuOp != LSU_LW.litValue &&
        lsuOp != LSU_LBU.litValue && lsuOp != LSU_LHU.litValue &&
        lsuOp != LSU_LR.litValue) {
      expect(c.io.decoder.lsuData, data)
    }
  }

  def expectCsr(csrOp: BigInt, addr: Int, data: BigInt) = {
    expect(c.io.decoder.csrOp, csrOp)
    if (csrOp != CSR_NOP.litValue) {
      expect(c.io.decoder.csrAddr, addr)
      expect(c.io.decoder.csrData, data)
    }
  }

  def expectExc(excType: BigInt) = expect(c.io.decoder.excType, excType)

  // add a1, a2, a1
  pokeDecoder(0x00b605b3)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(11), Some(12), Some(11))
  expectAlu(ALU_ADD, MDU_NOP, reg1, reg2)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // slti t0, t1, -3
  pokeDecoder(0xffd32293)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(5), Some(6), None)
  expectAlu(ALU_SLT, MDU_NOP, reg1, -3)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // auipc ra, 1048575
  pokeDecoder(0xfffff097)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(1), None, None)
  expectAlu(ALU_ADD, MDU_NOP, pc, 0xfffff000)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // slli a1, a1, 31
  pokeDecoder(0x01f59593)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(11), Some(11), None)
  expectAlu(ALU_SLL, MDU_NOP, reg1, 31)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // blt a1, a0, 40
  pokeDecoder(0x02a5c463)
  step(1)
  expectBranch(true, false, true, pc + 40)
  expectReg(None, Some(11), Some(10))
  expectAlu(ALU_ADD, MDU_NOP, 0, 0)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // jal s0, 353190
  pokeDecoder(0x3a65646f)
  step(1)
  expectBranch(true, true, true, pc + 353190)
  expectReg(Some(8), None, None)
  expectAlu(ALU_ADD, MDU_NOP, pc, 4)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_IADDR)

  // jalr ra, -1228(ra)
  pokeDecoder(0xb34080e7)
  step(1)
  expectBranch(true, true, true, (reg1u - 1228) & 0xfffffffe)
  expectReg(Some(1), Some(1), None)
  expectAlu(ALU_ADD, MDU_NOP, pc, 4)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // lb a0, -9(s0)
  pokeDecoder(0xff740503)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(10), Some(8), None)
  expectAlu(ALU_ADD, MDU_NOP, reg1, -9)
  expectLsu(LSU_LB, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_LOAD)

  // sw ra, 12(sp)
  pokeDecoder(0x00112623)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(None, Some(2), Some(1))
  expectAlu(ALU_ADD, MDU_NOP, reg1, 12)
  expectLsu(LSU_SW, reg2)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_STAMO)

  // csrrw sp, mscratch, sp
  pokeDecoder(0x34011173)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(2), Some(2), None)
  expectAlu(ALU_ADD, MDU_NOP, 0, 0)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_RW, 0x340, reg1u)
  expectExc(EXC_NONE)

  // csrrsi zero, mstatus, 8
  pokeDecoder(0x30046073)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(0), None, None)
  expectAlu(ALU_ADD, MDU_NOP, 0, 0)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_RS, 0x300, 8)
  expectExc(EXC_NONE)

  // csrr a0, mepc
  pokeDecoder(0x34102573)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(10), Some(0), None)
  expectAlu(ALU_ADD, MDU_NOP, 0, 0)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_R, 0x341, reg1u)
  expectExc(EXC_NONE)

  // csrw mepc, a0
  pokeDecoder(0x34151073)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(0), Some(10), None)
  expectAlu(ALU_ADD, MDU_NOP, 0, 0)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_W, 0x341, reg1u)
  expectExc(EXC_NONE)

  // mul a2, a0, a1
  pokeDecoder(0x02b50633)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(Some(12), Some(10), Some(11))
  expectAlu(ALU_ADD, MDU_MUL, reg1, reg2)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_NONE)

  // ecall
  pokeDecoder(0x00000073)
  step(1)
  expectBranch(false, false, false, 0)
  expectReg(None, None, None)
  expectAlu(ALU_ADD, MDU_NOP, 0, 0)
  expectLsu(LSU_NOP, 0)
  expectCsr(CSR_NOP, 0, 0)
  expectExc(EXC_ECALL)

  // illegal
  pokeDecoder(0x203d2067)
  step(1)
  expectExc(EXC_ILLEG)
}

object DecoderTest extends App {
  if (!Driver.execute(args, () => new Decoder) {
    (c) => new DecoderUnitTester(c)
  }) sys.exit(1)
}
