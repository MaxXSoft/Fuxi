package bpu

import utils.{PeekPokeTester, TestDriver}
import utils.InstEncoding._

class RasPredictionTester(c: BranchPredictor) extends PeekPokeTester(c) {
  poke(c.io.branchInfo.branch, false)
  poke(c.io.branchInfo.jump, false)
  poke(c.io.branchInfo.taken, false)
  poke(c.io.branchInfo.index, 0)
  poke(c.io.branchInfo.pc, 0)
  poke(c.io.branchInfo.target, 0)
  poke(c.io.flush, false)

  def lookup(inst: Long, pc: Long = 0x402, valid: Boolean = true,
             advance: Boolean = false): Unit = {
    poke(c.io.lookupPc, pc)
    poke(c.io.lookupInst, inst)
    poke(c.io.lookupValid, valid)
    poke(c.io.advance, advance)
  }
  def execute(inst: Long, pc: Long, valid: Boolean = true): Unit = {
    lookup(inst, pc, valid, advance = true)
    step(1)
  }
  def clear(): Unit = {
    lookup(addi(0, 0, 0))
    poke(c.io.flush, true)
    step(1)
    poke(c.io.flush, false)
  }
  def checkTop(address: Option[Long]): Unit = {
    lookup(jalr(0, 1, 0))
    expect(c.io.predTaken, address.nonEmpty)
    expect(c.io.predTarget, address.getOrElse(0L))
  }

  case class Hint(inst: Long, push: Boolean, pop: Boolean, bytes: Int)
  val hints = scala.collection.mutable.ArrayBuffer.empty[Hint]
  for (rd <- 0 until 32) hints += Hint(jal(rd, 16), Set(1, 5)(rd), false, 4)
  // Exercise every register pair against the five rows of the ISA hint table.
  for (rd <- 0 until 32; rs1 <- 0 until 32) {
    val (push, pop) = (Set(1, 5)(rd), Set(1, 5)(rs1), rd == rs1) match {
      case (false, false, _) => (false, false)
      case (false, true, _) => (false, true)
      case (true, false, _) => (true, false)
      case (true, true, false) => (true, true)
      case (true, true, true) => (true, false)
    }
    hints += Hint(jalr(rd, rs1, 0), push, pop, 4)
  }
  hints += Hint(c_jal(12), true, false, 2)
  hints += Hint(c_j(12), false, false, 2)
  for (rs1 <- 1 until 32) {
    hints += Hint(c_jr(rs1), false, Set(1, 5)(rs1), 2)
    hints += Hint(c_jalr(rs1), true, rs1 == 5, 2)
  }
  // RAS hints depend on registers, not the JALR immediate. The stack predicts
  // the saved continuation; Decode still checks the actual computed target.
  hints += Hint(jalr(0, 1, 8), false, true, 4)
  hints += Hint(jalr(1, 1, -8), true, false, 4)
  hints += Hint(jalr(5, 1, 8), true, true, 4)
  // Reserved JALR funct3, C.JR x0, C.EBREAK, C.MV and C.ADD must not act as hints.
  for (funct3 <- 1 until 8)
    hints += Hint(jalr(1, 5, 0) | (funct3 << 12), false, false, 4)
  for (inst <- Seq(0x8002L, 0x9002L, 0x8086L, 0x9086L, addi(1, 5, 0)))
    hints += Hint(inst, false, false, if ((inst & 3) == 3) 4 else 2)

  for (hint <- hints) {
    clear()
    execute(jal(1, 16), 0xa00)
    execute(c_jal(12), 0xb02)
    lookup(hint.inst, advance = true)
    expect(c.io.predTaken, hint.pop)
    if (hint.pop) expect(c.io.predTarget, 0xb04)
    step(1)
    var expected = Vector(0xa04L, 0xb04L)
    if (hint.pop) expected = expected.init
    if (hint.push) expected = expected :+ (0x402L + hint.bytes)
    for (address <- expected.reverse) {
      checkTop(Some(address))
      execute(jalr(0, 1, 0), 0x402)
    }
    checkTop(None)
  }

  // Invalid parcels/fetch faults and held instructions must not change RAS.
  clear()
  execute(jal(1, 16), 0xfffffffcL) // link wraps at XLEN, zero is a valid address
  checkTop(Some(0))
  execute(c_jal(12), 0x200, valid = false)
  execute(c_jr(1), 0x202, valid = false)
  lookup(c_jr(1), valid = false)
  expect(c.io.predTaken, false)
  checkTop(Some(0))
  for (inst <- Seq(jal(1, 16), c_jr(1).toLong)) {
    lookup(inst)
    step(20)
    checkTop(Some(0))
  }
  // Flush wins over a simultaneously accepted call, return, or coroutine.
  for (inst <- Seq(jal(1, 16), jalr(0, 1, 0), jalr(5, 1, 0))) {
    execute(jal(1, 16), 0x200)
    lookup(inst, advance = true)
    poke(c.io.flush, true)
    step(1)
    poke(c.io.flush, false)
    checkTop(None)
  }

  // An empty RAS falls back to BTB; a nonempty one overrides a stale target.
  poke(c.io.branchInfo.branch, true)
  poke(c.io.branchInfo.jump, true)
  poke(c.io.branchInfo.taken, true)
  poke(c.io.branchInfo.pc, 0x402)
  poke(c.io.branchInfo.target, 0xc00)
  step(1)
  poke(c.io.branchInfo.branch, false)
  poke(c.io.branchInfo.jump, false)
  lookup(c_jr(1))
  expect(c.io.predTaken, true)
  expect(c.io.predTarget, 0xc00)
  execute(c_jal(12), 0x200)
  lookup(c_jr(1))
  expect(c.io.predTarget, 0x202)
  execute(c_jr(1), 0x402)
  lookup(c_jr(1))
  expect(c.io.predTarget, 0xc00)
}

object RasPredictionTest extends App {
  if (!TestDriver.execute(args, () => new BranchPredictor) {
    c => new RasPredictionTester(c)
  }) sys.exit(1)
}
