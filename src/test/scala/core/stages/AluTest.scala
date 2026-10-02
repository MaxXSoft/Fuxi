package core.stages

import core.ALU
import chisel3.UInt
import utils.{PeekPokeTester, TestDriver}

import consts.AluOp._
import consts.MduOp._
import consts.CSR._
import consts.CsrOp._

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
      case ALU_SLT  => if (opr1 < opr2) 1 else 0
      case ALU_SLTU => if (unsignedCompare(opr1, opr2)) 1 else 0
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

  // Counter RMW must derive its explicit replacement from the returned EX
  // snapshot. Check both halves and boundary HPM slots; ordinary CSR handling
  // stays deferred so MIP's software/external pending-bit rule is preserved.
  poke(c.io.decoder.mduOp, MDU_NOP)
  poke(c.io.csrRead.valid, true)
  val snapshot = BigInt("a5a50055", 16)
  val operand = BigInt("00000033", 16)
  poke(c.io.csrRead.data, snapshot)
  poke(c.io.decoder.csrData, operand)
  val machineCounters = Seq(CSR_MCYCLE, CSR_MCYCLEH, CSR_MINSTRET, CSR_MINSTRETH,
    CSR_MHPMCOUNTER(3), CSR_MHPMCOUNTERH(3), CSR_MHPMCOUNTER(31), CSR_MHPMCOUNTERH(31))
  for (addr <- machineCounters; op <- Seq(CSR_RS, CSR_RC)) {
    poke(c.io.decoder.csrAddr, addr)
    poke(c.io.decoder.csrOp, op)
    expect(c.io.alu.reg.data, snapshot)
    expect(c.io.alu.csr.op, CSR_W)
    expect(c.io.alu.csr.data,
      if (op.litValue == CSR_RS.litValue) snapshot | operand else snapshot & ~operand)
    expect(c.io.csrRead.op, op)
  }
  for (addr <- Seq(CSR_MIP, CSR_MSTATUS, CSR_SSTATUS, CSR_MHPMEVENT(3), CSR_MCOUNTINHIBIT);
       op <- Seq(CSR_RS, CSR_RC)) {
    poke(c.io.decoder.csrAddr, addr)
    poke(c.io.decoder.csrOp, op)
    expect(c.io.alu.csr.op, op)
    expect(c.io.alu.csr.data, operand)
  }
}

object AluTest extends App {
  if (!TestDriver.execute(args, () => new ALU) {
    (c) => new AluUnitTester(c)
  }) sys.exit(1)
}
