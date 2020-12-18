package mdu

import chisel3.iotesters.{Driver, PeekPokeTester}

class DividerUnitTester(c: Divider) extends PeekPokeTester(c) {
  def testDivider() = {
    // generate operands and result
    val divident  = BigInt(rnd.nextInt.toHexString, 16)
    val divisor   = BigInt(rnd.nextInt.toHexString, 16)
    // println(s"divident: $divident, divisor: $divisor")
    val quotient  = divident / divisor
    val remainder = divident % divisor
    // poke & expect
    poke(c.io.en, true)
    poke(c.io.flush, false)
    poke(c.io.divident, divident)
    poke(c.io.divisor, divisor)
    while (peek(c.io.done) == 0) {
      step(1)
    }
    expect(c.io.quotient, quotient)
    expect(c.io.remainder, remainder)
    step(1)
  }

  for (i <- 0 until 20) {
    testDivider()
  }
}

object DividerTest extends App {
  if (!Driver.execute(args, () => new Divider(32)) {
    (c) => new DividerUnitTester(c)
  }) sys.exit(1)
}
