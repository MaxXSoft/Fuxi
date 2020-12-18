package mdu

import chisel3.iotesters.{Driver, PeekPokeTester}

class MultiplierUnitTester(c: Multiplier) extends PeekPokeTester(c) {
  val mask = BigInt("ffffffff", 16)

  def testMult() = {
    // generate operands & result
    val opr1    = BigInt(rnd.nextInt.toHexString, 16)
    val opr2    = BigInt(rnd.nextInt.toHexString, 16)
    val result  = opr1 * opr2
    // test multiplier
    poke(c.io.en, true)
    poke(c.io.opr1, opr1)
    poke(c.io.opr2, opr2)
    while (peek(c.io.done) == 0) {
      step(1)
      poke(c.io.en, false)
    }
    expect(c.io.result, result)
    step(1)
  }

  // normal
  for (i <- 0 until 20) {
    testMult()
  }
}

object MultiplierTest extends App {
  if (!Driver.execute(args, () => new Multiplier(32, 3)) {
    (c) => new MultiplierUnitTester(c)
  }) sys.exit(1)
}
