package lsu

import chisel3.iotesters.{Driver, PeekPokeTester}

import LsuDecode._

class AmoExecuteUnitTester(c: AmoExecute) extends PeekPokeTester(c) {
  def testAmo() = {
    val opr1 = BigInt(rnd.nextInt.toHexString, 16)
    val opr2 = BigInt(rnd.nextInt.toHexString, 16)
    val mask = BigInt("ffffffff", 16)

    def simulateLatency() = {
      val latency = rnd.nextInt(32)
      for (i <- 0 until latency) {
        poke(c.io.ramValid, false)
        step(1)
      }
      poke(c.io.ramValid, true)
      poke(c.io.ramRdata, opr1)
      step(1)
    }

    poke(c.io.op, AMO_OP_ADD)
    poke(c.io.flush, false)
    poke(c.io.regOpr, opr2)
    poke(c.io.ramRdata, 0)
    simulateLatency()
    expect(c.io.ready, false)
    expect(c.io.ramWen, true)
    expect(c.io.ramWdata, (opr1 + opr2) & mask)
    simulateLatency()
    expect(c.io.ready, true)
    expect(c.io.regWdata, opr1)
    step(1)
  }

  for (i <- 0 until 20) {
    testAmo()
  }
}

object AmoExecuteTest extends App {
  if (!Driver.execute(args, () => new AmoExecute) {
    (c) => new AmoExecuteUnitTester(c)
  }) sys.exit(1)
}
