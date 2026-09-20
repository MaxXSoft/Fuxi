package mdu

import utils.{PeekPokeTester, TestDriver}

class DividerUnitTester(c: Divider) extends PeekPokeTester(c) {
  poke(c.io.en, false)
  poke(c.io.flush, false)
  poke(c.io.divident, 0)
  poke(c.io.divisor, 0)

  def testDivider(divident: BigInt, divisor: BigInt): Int = {
    poke(c.io.en, true)
    poke(c.io.divident, divident)
    poke(c.io.divisor, divisor)
    var cycles = 0
    while (peek(c.io.done) == 0 && cycles < c.cycleCount + 2) {
      step(1)
      cycles += 1
    }
    assert(peek(c.io.done) != 0, s"division $divident / $divisor timed out")
    expect(c.io.divZero, divisor == 0)
    // MDU, rather than Divider, supplies the architectural zero-divisor result.
    if (divisor != 0) {
      expect(c.io.quotient, divident / divisor)
      expect(c.io.remainder, divident % divisor)
    }
    poke(c.io.en, false)
    step(1)
    expect(c.io.done, false)
    cycles
  }

  def flush(): Unit = {
    poke(c.io.flush, true)
    step(1)
    expect(c.io.done, false)
    poke(c.io.flush, false)
    poke(c.io.en, false)
  }

  // Reset operand tags are zero, but there is no completed result to reuse.
  testDivider(0, 0)

  // A valid cached quotient/remainder must still be reusable without rerunning
  // the iterative calculation, including after unrelated disabled inputs.
  val coldCycles = testDivider(100, 7)
  poke(c.io.divident, 53)
  poke(c.io.divisor, 4)
  step(3)
  assert(testDivider(100, 7) < coldCycles, "completed division was not reused")

  // An interrupt or fence may flush the pipeline while the divider is idle.
  flush()
  testDivider(100, 7)

  // Cancel at every iteration boundary, including just before completion and
  // with done asserted. Retrying the same operands must not reuse partial or
  // cleared results. Keep en asserted during flush to check its priority.
  for (elapsed <- 0 to c.cycleCount) {
    val divident = BigInt("fedcba98", 16) - elapsed
    val divisor = BigInt(37 + elapsed)
    poke(c.io.en, true)
    poke(c.io.divident, divident)
    poke(c.io.divisor, divisor)
    step(1)
    step(elapsed)
    expect(c.io.done, elapsed == c.cycleCount)
    flush()
    testDivider(divident, divisor)
  }

  // Reset and flush both invalidate a completed result, including divZero.
  poke(c.reset, 1)
  step(1)
  poke(c.reset, 0)
  testDivider(0, 0)
  flush()
  testDivider(0, 0)
  testDivider(123, 0)
  flush()
  testDivider(123, 0)
  testDivider(123, 7)

  // Keep the arithmetic sample reproducible alongside the directed cases.
  rnd.setSeed(0x46555849L)
  for (i <- 0 until 20) {
    val divident = BigInt(rnd.nextInt().toHexString, 16)
    val divisor = BigInt(rnd.nextInt().toHexString, 16)
    testDivider(divident, divisor)
  }
}

object DividerTest extends App {
  if (!TestDriver.execute(args, () => new Divider(32)) {
    (c) => new DividerUnitTester(c)
  }) sys.exit(1)
}
