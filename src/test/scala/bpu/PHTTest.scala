package bpu

import configs.{DefaultConfig, WithGhrBits}
import org.scalatest.funsuite.AnyFunSuite
import utils.{PeekPokeTester, TestDriver}

class PhtCounterTester(c: PHT, entries: Int) extends PeekPokeTester(c) {
  val reference = Array.fill(entries)(2)
  def checkAll(): Unit = {
    for (i <- reference.indices) {
      poke(c.io.index, i)
      expect(c.io.taken, reference(i) >= 2)
    }
  }
  def train(index: Int, taken: Boolean, enable: Boolean = true): Unit = {
    poke(c.io.lastIndex, index)
    poke(c.io.lastTaken, taken)
    poke(c.io.lastBranch, enable)
    checkAll() // no combinational change before the update edge
    step(1)
    if (enable) reference(index) =
      math.max(0, math.min(3, reference(index) + (if (taken) 1 else -1)))
    checkAll() // other counters must not change
  }
  poke(c.io.lastBranch, false)
  poke(c.io.lastTaken, false)
  poke(c.io.lastIndex, 0)
  checkAll()
  // Reconstruct all four states and exercise both transitions, including
  // both saturation boundaries. Follow-up steps expose low-bit errors via
  // the public prediction bit rather than probing implementation registers.
  for (i <- 0 until entries; state <- 0 to 3; taken <- Seq(false, true)) {
    for (_ <- 0 until 3) train(i, false)
    for (_ <- 0 until state) train(i, true)
    train(i, !taken, enable = false)
    train(i, taken)
    for (_ <- 0 until 3) train(i, false)
    for (_ <- 0 until 3) train(i, true)
  }
}

class PHTTest extends AnyFunSuite {
  test("preserve every saturating transition and isolate counter updates") {
    for (bits <- Seq(2, 5)) {
      val p = new WithGhrBits(bits) ++ new DefaultConfig
      TestDriver.run(new PHT()(p)) { c => new PhtCounterTester(c, 1 << bits) }
    }
  }
}
