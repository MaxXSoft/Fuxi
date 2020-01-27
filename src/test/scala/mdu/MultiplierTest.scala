package mdu

import chisel3.iotesters.{Driver, PeekPokeTester}

class MultiplierUnitTester(c: Multiplier) extends PeekPokeTester(c) {
  val mask = BigInt("ffffffff", 16)

  def testMult() = {
    // generate operands & result
    val opr1    = BigInt(rnd.nextInt.toHexString, 16)
    val opr2    = BigInt(rnd.nextInt.toHexString, 16)
    val result  = opr1 * opr2
    val lo      = result & mask
    val hi      = (result >> 32) & mask
    // test multiplier
    poke(c.io.en, true)
    poke(c.io.opr1, opr1)
    poke(c.io.opr2, opr2)
    do {
      step(1)
      poke(c.io.en, false)
    } while (peek(c.io.done) == 0)
    expect(c.io.lo, lo)
    expect(c.io.hi, hi)
  }

  for (i <- 0 until 20) {
    testMult()
  }
}

object MultiplierTest extends App {
  Driver.execute(args, () => new Multiplier(32, 2)) {
    (c) => new MultiplierUnitTester(c)
  }
}
