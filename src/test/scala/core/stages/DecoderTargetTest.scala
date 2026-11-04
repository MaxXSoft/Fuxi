package core.stages

import org.scalatest.funsuite.AnyFunSuite

import core.Decoder
import consts.ExceptType.EXC_NONE
import utils.DefaultTestConfig._
import utils.InstEncoding._
import utils.{PeekPokeTester, TestDriver}

private class DecoderTargetTester(c: Decoder) extends PeekPokeTester(c) {
  val mask = BigInt("ffffffff", 16)
  def u32(value: BigInt): BigInt = value & mask
  case class Case(inst: Long, pc: BigInt, a: BigInt, b: BigInt,
                  target: BigInt, taken: Boolean, jump: Boolean, bytes: Int = 4,
                  branch: Boolean = true)
  val cases = scala.collection.mutable.ArrayBuffer.empty[Case]
  val pcs = Seq(BigInt(0), BigInt(0x202), BigInt("7ffffffe", 16), mask - 1)
  for (pc <- pcs; offset <- Seq(-1048576, -2, 0, 2, 1048574)) {
    cases += Case(jal(1, offset), pc, 0, 0, u32(pc + offset), true, true)
  }
  for (base <- Seq(BigInt(0), BigInt(1), BigInt(0x307), BigInt("7fffffff", 16), mask);
       offset <- Seq(-2048, -1, 0, 2047)) {
    cases += Case(jalr(1, 2, offset), 0x202, base, 0,
                  u32(base + offset) & ~BigInt(1), true, true)
  }
  val operands = Seq((0L, 0L), (1L, 2L), (2L, 1L), (0xffffffffL, 0L),
                     (0x80000000L, 0x7fffffffL), (0x7fffffffL, 0x80000000L))
  for (funct <- Seq(0, 1, 4, 5, 6, 7); (a, b) <- operands;
       (offset, pc) <- Seq(-4096, -2, 2, 4094).zip(pcs)) {
    val taken = funct match {
      case 0 => a == b
      case 1 => a != b
      case 4 => a.toInt < b.toInt
      case 5 => a.toInt >= b.toInt
      case 6 => a < b
      case 7 => a >= b
    }
    cases += Case(beq(2, 3, offset) | (funct.toLong << 12), pc, a, b,
                  u32(pc + offset), taken, false)
  }
  for (offset <- Seq(-2048, -2, 2, 2046); pc <- pcs;
       inst <- Seq(c_j(offset), c_jal(offset))) {
    cases += Case(inst, pc, 0, 0, u32(pc + offset), true, true, bytes = 2)
  }
  for (inst <- Seq(c_jr(1), c_jalr(1)); base <- Seq(BigInt(1), mask)) {
    cases += Case(inst, 0x202, base, 0, base & ~BigInt(1), true, true, bytes = 2)
  }
  for (a <- Seq(0, 1); offset <- Seq(-256, 254); equal <- Seq(false, true)) {
    val inst = if (equal) c_beqz(8, offset) else c_bnez(8, offset)
    cases += Case(inst, 0x202, a, 0, u32(BigInt(0x202) + offset),
                  (a == 0) == equal, false, bytes = 2)
  }
  cases += Case(addi(1, 2, 7), 0x202, 0x123, 0, 0, false, false, branch = false)
  cases += Case(c_nop(), 0x202, 0, 0, 0, false, false, bytes = 2, branch = false)

  poke(c.io.fetch.valid, true)
  poke(c.io.fetch.pageFault, false)
  poke(c.io.fetch.accessFault, false)
  poke(c.io.fetch.predIndex, 0)
  poke(c.io.fetch.pmu.rasCandidate, false)
  poke(c.io.fetch.pmu.rasUsed, false)
  for (v <- cases) {
    poke(c.io.fetch.inst, v.inst)
    poke(c.io.fetch.pc, v.pc)
    poke(c.io.read1.data, v.a)
    poke(c.io.read2.data, v.b)
    // Toggle every target bit, including bit zero: JALR clears the computed
    // address's low bit, but must still reject an odd *predicted* target.
    for (predicted <- Seq(v.target) ++ (0 until 32).map(bit => v.target ^ (BigInt(1) << bit));
         predictedTaken <- Seq(false, true)) {
      val directionMiss = predictedTaken != v.taken
      val targetMiss = predictedTaken && v.taken && predicted != v.target
      val miss = directionMiss || targetMiss
      poke(c.io.fetch.taken, predictedTaken)
      poke(c.io.fetch.target, predicted)
      poke(c.io.stallId, false)
      poke(c.io.flush, false)
      expect(c.io.decoder.excType, EXC_NONE)
      expect(c.io.branch.target, v.target)
      expect(c.io.branch.branch, v.branch)
      expect(c.io.branch.jump, v.jump)
      expect(c.io.branch.taken, v.taken)
      expect(c.io.flushPc, if (v.taken) v.target else u32(v.pc + v.bytes))
      expect(c.io.flushIf, miss)
      expect(c.io.decoder.pmu.directionMiss, directionMiss)
      expect(c.io.decoder.pmu.targetMiss, targetMiss)
      expect(c.io.decoder.pmu.miss, miss)
    }
    // A held or flushed branch must not recover/train even with a wrong target.
    for ((stalled, flushed) <- Seq((true, false), (false, true))) {
      poke(c.io.stallId, stalled)
      poke(c.io.flush, flushed)
      expect(c.io.flushIf, false)
      expect(c.io.branch.branch, false)
      expect(c.io.branch.jump, false)
      expect(c.io.branch.taken, false)
    }
  }
}

class DecoderTargetTest extends AnyFunSuite {
  test("validate branch targets and prediction metadata across jump and branch forms") {
    TestDriver.run(new Decoder) { c => new DecoderTargetTester(c) }
  }
}
