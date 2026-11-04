package core.stages

import org.scalatest.funsuite.AnyFunSuite

import core.Decoder
import consts.ExceptType._
import consts.LsuOp.LSU_NOP
import consts.MduOp.MDU_NOP
import consts.CsrOp.CSR_NOP
import utils.DefaultTestConfig._
import utils.InstEncoding._
import utils.{PeekPokeTester, TestDriver}

class DecoderFaultTester(c: Decoder) extends PeekPokeTester(c) {
  val legal = Seq(add(1, 2, 3), lw(1, 2, 0), sw(3, 2, 0), mul(1, 2, 3),
                  csrrw(1, 0x340, 2), jal(1, 8), jalr(1, 2, 0), beq(2, 3, 8),
                  c_jal(8).toLong, c_jalr(1).toLong, c_lwsp(1, 4).toLong,
                  c_swsp(1, 4).toLong, c_ebreak().toLong)
  val illegal = Seq(0xffffffffL, 0x203d2067L, 0x0000L, 0x2000L, 0x4002L, 0x8002L)
  poke(c.io.fetch.valid, true)
  poke(c.io.fetch.predIndex, 0)
  poke(c.io.fetch.taken, true) // A stale prediction must not redirect a fault.
  poke(c.io.fetch.target, 0x800)
  poke(c.io.fetch.faultAddr, 0x1000)

  for (inst <- legal ++ illegal; fault <- 0 until 4 if fault != 0 || illegal.contains(inst);
       pc <- Seq(0x200, 0x201); stopped <- Seq(false, true)) {
    poke(c.io.fetch.inst, inst)
    poke(c.io.fetch.pc, pc)
    poke(c.io.fetch.pageFault, (fault & 1) != 0)
    poke(c.io.fetch.accessFault, (fault & 2) != 0)
    poke(c.io.stallId, stopped)
    poke(c.io.flush, stopped)
    for ((a, b) <- Seq((0L, 0L), (0xffffffffL, 0L), (1L, 1L))) {
      poke(c.io.read1.data, a)
      poke(c.io.read2.data, b)
      val expected = if ((fault & 1) != 0) EXC_IPAGE
                     else if ((fault & 2) != 0) EXC_IACCESS else EXC_ILLEG
      expect(c.io.decoder.excType, expected)
      expect(c.io.decoder.excValue, if (fault != 0) 0x1000 else 0)
      expect(c.io.decoder.inst, inst)
      expect(c.io.decoder.lsuOp, LSU_NOP)
      expect(c.io.decoder.mduOp, MDU_NOP)
      expect(c.io.decoder.csrOp, CSR_NOP)
      expect(c.io.decoder.regWen, false)
      expect(c.io.branch.branch, false)
      expect(c.io.branch.jump, false)
      expect(c.io.branch.taken, false)
      expect(c.io.flushIf, false)
    }
  }

  // Address faults still apply to a legal taken jump. Fetch faults above
  // must override them; invalid encodings at the same odd PC stay illegal.
  poke(c.io.fetch.inst, jal(1, 8))
  poke(c.io.fetch.pc, 0x201)
  poke(c.io.fetch.pageFault, false)
  poke(c.io.fetch.accessFault, false)
  poke(c.io.stallId, false)
  poke(c.io.flush, false)
  expect(c.io.decoder.excType, EXC_IADDR)
  expect(c.io.decoder.excValue, 0x209)
  expect(c.io.flushIf, false)

  // Invalid FIFO contents have no architectural instruction to train/recover.
  for (inst <- illegal) {
    poke(c.io.fetch.inst, inst)
    poke(c.io.fetch.valid, false)
    expect(c.io.decoder.valid, false)
    expect(c.io.decoder.excType, EXC_NONE)
    expect(c.io.branch.branch, false)
    expect(c.io.branch.jump, false)
    expect(c.io.flushIf, false)
  }
}

class DecoderFaultTest extends AnyFunSuite {
  test("suppress operations and prediction updates on illegal instructions and fetch faults") {
    TestDriver.run(new Decoder) { c => new DecoderFaultTester(c) }
  }
}
