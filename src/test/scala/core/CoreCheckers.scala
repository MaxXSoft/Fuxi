package core

import consts.Parameters.DATA_WIDTH

case class ObservedInstruction(pc: BigInt, write: Option[(Int, BigInt)])

object CoreChecks {
  def writeback(actual: ObservedInstruction, expected: ExpectedWriteback): Unit = {
    val data = if (expected.data < 0) expected.data & ((BigInt(1) << DATA_WIDTH) - 1)
               else expected.data
    assert(actual.pc == expected.pc && actual.write.contains(expected.rd -> data),
      s"Writeback $actual; expected $expected")
  }
}

// Selected checkpoints, with the same unrelated-writeback policy as CoreProgramTester.
class WritebackChecker(expected: Seq[ExpectedWriteback]) {
  private val pending = scala.collection.mutable.Queue.from(expected)
  private val checkedPcs = expected.map(_.pc).toSet
  require(checkedPcs.size == expected.size, "Duplicate writeback checkpoint PC")
  def missing: String = pending.mkString(", ")
  def observe(actual: ObservedInstruction): Unit = {
    if (actual.write.nonEmpty && checkedPcs(actual.pc)) {
      assert(pending.nonEmpty, s"Repeated writeback at PC 0x${actual.pc.toString(16)}")
      CoreChecks.writeback(actual, pending.front)
      pending.dequeue()
    }
  }
  def checkComplete(): Unit = assert(pending.isEmpty, s"Missing writebacks: $missing")
}

// Every retirement is checked in execution order, including repeated PCs.
// Call observe only when the retirement signal is asserted; this never steps.
class RetirementChecker(expected: Seq[ExpectedRetirement]) {
  private val pending = scala.collection.mutable.Queue.from(expected)
  private var observed = 0
  def count: Int = observed
  def missing: String = pending.mkString(", ")
  def observe(actual: ObservedInstruction): Unit = {
    assert(pending.nonEmpty, s"Unexpected retirement: $actual")
    val next = pending.front
    assert(actual.pc == next.pc, s"Retired PC 0x${actual.pc.toString(16)}; expected $next")
    next.write.foreach { case (rd, data) =>
      CoreChecks.writeback(actual, ExpectedWriteback(next.pc, rd, data))
    }
    pending.dequeue()
    observed += 1
  }
  // Optional: CSR-write tests have their own counter model.
  def checkCount(actual: BigInt): Unit =
    assert(actual == observed, s"minstret $actual; expected $observed")
  def checkComplete(): Unit = assert(pending.isEmpty, s"Missing retirements: $missing")
}

class TrapChecker(expected: Seq[ExpectedTrap]) {
  private val pending = scala.collection.mutable.Queue.from(expected)
  def missing: String = pending.mkString(", ")
  def observe(pc: BigInt, cause: BigInt, value: BigInt): Unit = {
    val actual = ExpectedTrap(pc, cause, value)
    assert(pending.nonEmpty, s"Unexpected trap: $actual")
    assert(actual == pending.front, s"Trap $actual; expected ${pending.front}")
    pending.dequeue()
  }
  def checkComplete(): Unit = assert(pending.isEmpty, s"Missing traps: $missing")
}
