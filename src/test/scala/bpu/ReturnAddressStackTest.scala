package bpu

import utils.{PeekPokeTester, TestDriver}

class ReturnAddressStackTester(c: ReturnAddressStack, depth: Int) extends PeekPokeTester(c) {
  var expected = Vector.empty[BigInt]
  def check(): Unit = {
    expect(c.io.valid, expected.nonEmpty)
    expect(c.io.top, expected.lastOption.getOrElse(BigInt(0)))
  }
  def cycle(push: Boolean = false, pop: Boolean = false, address: BigInt = 0,
            flush: Boolean = false): Unit = {
    poke(c.io.flush, flush)
    poke(c.io.push, push)
    poke(c.io.pop, pop)
    poke(c.io.pushAddr, address)
    check() // simultaneous pop/push must still expose the old top
    expect(c.io.pmu.overflow, !flush && push && !pop && expected.size == depth)
    expect(c.io.pmu.emptyPop, !flush && pop && expected.isEmpty)
    expect(c.io.pmu.clearNonempty, flush && expected.nonEmpty)
    if (flush) expected = Vector.empty
    else {
      if (pop && expected.nonEmpty) expected = expected.init
      if (push) expected = (expected :+ address).takeRight(depth)
    }
    step(1)
    check()
  }

  cycle(pop = true) // empty underflow
  cycle(push = true, pop = true, address = 0x202) // empty pop-then-push
  cycle(flush = true)
  for (i <- 0 until depth * 3) cycle(push = true, address = 0x80000002L + i * 2)
  cycle(push = true, pop = true, address = 0xfffffffeL) // full replacement
  for (_ <- 0 until depth + 2) cycle(pop = true)
  for (i <- 0 until depth) cycle(push = true, address = 0x400 + 4 * i)
  cycle(push = true, pop = true, address = 0x100, flush = true)
  cycle(pop = true)
  cycle(push = true, address = 0x102)
  cycle(push = true, address = 0x104, flush = true)

  val random = new scala.util.Random(0x524153L + depth)
  for (_ <- 0 until 2000) {
    cycle(push = random.nextBoolean(), pop = random.nextBoolean(),
          address = BigInt(random.nextInt().toLong & 0xfffffffeL),
          flush = random.nextInt(31) == 0)
  }
}

object ReturnAddressStackTest extends App {
  // Include a one-entry stack and a non-power-of-two stack to exercise wrap.
  for (depth <- Seq(1, 3, 16)) {
    if (!TestDriver.execute(args, () => new ReturnAddressStack(depth)) {
      c => new ReturnAddressStackTester(c, depth)
    }) sys.exit(1)
  }
}
