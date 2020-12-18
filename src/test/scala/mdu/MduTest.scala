package mdu

import chisel3.UInt
import chisel3.iotesters.{Driver, PeekPokeTester}

import consts.MduOp._

class MduUnitTester(c: MDU) extends PeekPokeTester(c) {
  val mask  = BigInt("ffffffff", 16)
  val min   = BigInt("80000000", 16)

  def generateOprAns(op: UInt) = {
    val num1  = rnd.nextInt
    val num2  = rnd.nextInt
    val opr1s = BigInt(num1)
    val opr2s = BigInt(num2)
    val opr1  = BigInt(num1.toHexString, 16)
    val opr2  = BigInt(num2.toHexString, 16)
    val ans: BigInt = op match {
      case MDU_MUL => (opr1 * opr2) & mask
      case MDU_MULH => ((opr1s * opr2s) >> 32) & mask
      case MDU_MULHSU => ((opr1s * opr2) >> 32) & mask
      case MDU_MULHU => ((opr1 * opr2) >> 32) & mask
      case MDU_DIV => if (opr2 == 0) {
                        mask
                      } else if (opr1 == min && opr2s == -1) {
                        min
                      } else {
                        (opr1s / opr2s) & mask
                      }
      case MDU_DIVU => if (opr2 == 0) mask else opr1 / opr2
      case MDU_REM => if (opr2 == 0) {
                        opr1
                      } else if (opr1 == min && opr2s == -1) {
                        0
                      } else {
                        (opr1s % opr2s) & mask
                      }
      case MDU_REMU => if (opr2 == 0) opr1 else opr1 % opr2
      case _ => 0
    }
    (opr1, opr2, ans)
  }

  def testMdu(op: UInt) = {
    val (opr1, opr2, ans) = generateOprAns(op)
    poke(c.io.flush, false)
    poke(c.io.op, op)
    poke(c.io.opr1, opr1)
    poke(c.io.opr2, opr2)
    while (peek(c.io.valid) == 0) {
      step(1)
    }
    expect(c.io.result, ans)
    step(1)
  }

  for (i <- 0 until 20) {
    testMdu(MDU_NOP)
    testMdu(MDU_MUL)
    testMdu(MDU_MULH)
    testMdu(MDU_MULHSU)
    testMdu(MDU_MULHU)
    testMdu(MDU_DIV)
    testMdu(MDU_DIVU)
    testMdu(MDU_REM)
    testMdu(MDU_REMU)
  }
}

object MduTest extends App {
  if (!Driver.execute(args, () => new MDU) {
    (c) => new MduUnitTester(c)
  }) sys.exit(1)
}
