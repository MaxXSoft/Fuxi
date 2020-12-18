package core

import chisel3.UInt
import chisel3.iotesters.{Driver, PeekPokeTester}

import consts.AluOp._
import consts.MduOp._

class AluUnitTester(c: ALU) extends PeekPokeTester(c) {
  def unsignedCompare(i : Long, j : Long) = (i < j) ^ (i < 0) ^ (j < 0)

  def testAlu(aluOp: UInt) = {
    val opr1 = rnd.nextInt()
    val opr2 = rnd.nextInt()
    val result = aluOp match {
      case ALU_ADD  => opr1 + opr2
      case ALU_SUB  => opr1 - opr2
      case ALU_XOR  => opr1 ^ opr2
      case ALU_OR   => opr1 | opr2
      case ALU_AND  => opr1 & opr2
      case ALU_SLT  => (opr1 < opr2).toInt
      case ALU_SLTU => unsignedCompare(opr1, opr2).toInt
      case ALU_SLL  => opr1 << (opr2 & 0x1f)
      case ALU_SRL  => opr1 >>> (opr2 & 0x1f)
      case ALU_SRA  => opr1 >> (opr2 & 0x1f)
      case _        => 0
    }
    poke(c.io.decoder.aluOp, aluOp)
    poke(c.io.decoder.opr1, opr1)
    poke(c.io.decoder.opr2, opr2)
    step(1)
    expect(c.io.alu.reg.data, BigInt(result.toHexString, 16))
  }

  // test ALU
  for (i <- 0 until 20) {
    testAlu(ALU_ADD)
    testAlu(ALU_SUB)
    testAlu(ALU_XOR)
    testAlu(ALU_OR)
    testAlu(ALU_AND)
    testAlu(ALU_SLT)
    testAlu(ALU_SLTU)
    testAlu(ALU_SLL)
    testAlu(ALU_SRL)
    testAlu(ALU_SRA)
  }

  // test division
  poke(c.io.decoder.mduOp, MDU_DIV)
  poke(c.io.decoder.opr1, 0x123456)
  poke(c.io.decoder.opr2, 0x123)
  while (peek(c.io.stallReq) != 0) {
    step(1)
  }
  expect(c.io.alu.reg.data, 0x123456 / 0x123)
  step(1)
}

object AluTest extends App {
  if (!Driver.execute(args, () => new ALU) {
    (c) => new AluUnitTester(c)
  }) sys.exit(1)
}
