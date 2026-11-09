package core.stages

import core.Decoder
import consts.ExceptType.EXC_NONE
import consts.LsuOp.{LSU_FENC, LSU_FENI}
import org.scalatest.funsuite.AnyFunSuite
import utils.DefaultTestConfig._
import utils.{PeekPokeTester, TestDriver}

class DecoderFenceTester(c: Decoder) extends PeekPokeTester(c) {
  poke(c.io.fetch.valid, true)
  poke(c.io.fetch.pc, 0x200)
  poke(c.io.fetch.predIndex, 0)
  poke(c.io.fetch.taken, false)
  poke(c.io.fetch.target, 0)
  poke(c.io.fetch.faultAddr, 0)
  poke(c.io.fetch.pageFault, false)
  poke(c.io.fetch.accessFault, false)
  poke(c.io.stallId, false)
  poke(c.io.flush, false)
  poke(c.io.read1.data, 0x12345678)
  poke(c.io.read2.data, 0x87654321L)

  // Include TSO (fm=8, pred=succ=RW), reserved modes, empty sets/HINTs,
  // and nonzero register fields. None may access an architectural register.
  for (fm <- 0 until 16; sets <- Seq(0, 0x10, 0x33, 0xff);
       rs1 <- Seq(0, 1, 31); rd <- Seq(0, 1, 31)) {
    poke(c.io.fetch.inst, (fm.toLong << 28) | (sets.toLong << 20) |
      (rs1.toLong << 15) | (rd.toLong << 7) | 0x0fL)
    expect(c.io.decoder.excType, EXC_NONE)
    expect(c.io.decoder.lsuOp, LSU_FENC)
    expect(c.io.read1.en, false)
    expect(c.io.read2.en, false)
    expect(c.io.decoder.regWen, false)
    expect(c.io.branch.branch, false)
  }
  for (funct12 <- Seq(0, 1, 0x800, 0xfff);
       rs1 <- Seq(0, 1, 31); rd <- Seq(0, 1, 31)) {
    poke(c.io.fetch.inst, (funct12.toLong << 20) | (rs1.toLong << 15) |
      (rd.toLong << 7) | 0x100fL)
    expect(c.io.decoder.excType, EXC_NONE)
    expect(c.io.decoder.lsuOp, LSU_FENI)
    expect(c.io.read1.en, false)
    expect(c.io.read2.en, false)
    expect(c.io.decoder.regWen, false)
  }
}

class DecoderFenceTest extends AnyFunSuite {
  test("accept TSO and ignore unused fence fields without register effects") {
    TestDriver.run(new Decoder)(c => new DecoderFenceTester(c))
  }
}
