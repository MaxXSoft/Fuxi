package core

import consts.Parameters.DATA_WIDTH

case class ObservedInstruction(pc: BigInt, write: Option[(Int, BigInt)])
case class ObservedTrap(pc: BigInt, cause: BigInt, value: BigInt)

trait Checker {
  def missing: String
  def checkComplete(): Unit
}

trait EventChecker[-A] extends Checker {
  def observe(actual: A): Unit
}

// Own the queue and consume only after validation succeeds. Subclasses decide
// which observed events participate and how an event matches its expectation.
abstract class OrderedChecker[E, A](expected: Seq[E], label: String) extends EventChecker[A] {
  private val pending = scala.collection.mutable.Queue.from(expected)
  final def missing: String = pending.mkString(", ")
  final def checkComplete(): Unit = assert(pending.isEmpty, s"Missing $label: $missing")

  protected final def consume(actual: A)(check: (A, E) => Unit): Unit = {
    assert(pending.nonEmpty, s"Unexpected $label: $actual")
    check(actual, pending.front)
    pending.dequeue()
  }
}

object CoreChecks {
  def writeback(actual: ObservedInstruction, expected: ExpectedWriteback): Unit = {
    val data = if (expected.data < 0) expected.data & ((BigInt(1) << DATA_WIDTH) - 1)
               else expected.data
    assert(actual.pc == expected.pc && actual.write.contains(expected.rd -> data),
      s"Writeback $actual; expected $expected")
  }
}

// Check selected (PC, occurrence) pairs in declaration order. Unselected writes
// are allowed, including later visits to a checked PC. Count only write events,
// not cycles or loop iterations; checkOccurrences can also constrain the total.
class WritebackChecker(expected: Seq[ExpectedWriteback])
    extends OrderedChecker[ExpectedWriteback, ObservedInstruction](expected, "writebacks") {
  private val checkpoints = expected.map(e => e.pc -> e.occurrence).toSet
  require(checkpoints.size == expected.size, "Duplicate writeback checkpoint (PC, occurrence)")
  require(expected.groupBy(_.pc).values.forall { checks =>
    val visits = checks.map(_.occurrence)
    visits == visits.sorted
  }, "Writeback occurrences for each PC must be increasing")
  private val seen = scala.collection.mutable.Map.empty[BigInt, Int]

  def occurrences(pc: BigInt): Int = seen.getOrElse(pc, 0)
  def checkOccurrences(pc: BigInt, total: Int): Unit = {
    require(total >= 0)
    assert(occurrences(pc) == total,
      s"Writebacks at PC 0x${pc.toString(16)}: ${occurrences(pc)}; expected $total")
  }
  def observe(actual: ObservedInstruction): Unit = {
    if (actual.write.nonEmpty) {
      val occurrence = occurrences(actual.pc) + 1
      if (checkpoints(actual.pc -> occurrence)) consume(actual) { (event, next) =>
        assert(event.pc == next.pc && occurrence == next.occurrence,
          s"Writeback at PC 0x${event.pc.toString(16)}, occurrence $occurrence; expected $next")
        CoreChecks.writeback(event, next)
      }
      seen(actual.pc) = occurrence
    }
  }
}

// Every retirement is checked in execution order, including repeated PCs.
// Call observe only when the retirement signal is asserted; this never steps.
class RetirementChecker(expected: Seq[ExpectedRetirement])
    extends OrderedChecker[ExpectedRetirement, ObservedInstruction](expected, "retirements") {
  private var observed = 0
  def count: Int = observed
  def observe(actual: ObservedInstruction): Unit = {
    consume(actual) { (event, next) =>
      assert(event.pc == next.pc, s"Retired PC 0x${event.pc.toString(16)}; expected $next")
      next.write.foreach { case (rd, data) =>
        CoreChecks.writeback(event, ExpectedWriteback(next.pc, rd, data))
      }
    }
    observed += 1
  }
  // Optional: CSR-write tests have their own counter model.
  def checkCount(actual: BigInt): Unit =
    assert(actual == observed, s"minstret $actual; expected $observed")
}

class TrapChecker(expected: Seq[ExpectedTrap])
    extends OrderedChecker[ExpectedTrap, ObservedTrap](expected, "traps") {
  def observe(actual: ObservedTrap): Unit = consume(actual) { (event, next) =>
    assert(event == ObservedTrap(next.pc, next.cause, next.value), s"Trap $event; expected $next")
  }
}
