package bus

import chisel3.iotesters.{Driver, PeekPokeTester}

class TlbTester(c: TLB) extends PeekPokeTester(c) {
  def pokeTlbEntry(ppn: Int) = {
    poke(c.io.wen, true)
    poke(c.io.went.ppn, ppn)
  }

  def expectTlbEntry(ppn: Int) = {
    expect(c.io.valid, true)
    expect(c.io.rent.ppn, ppn)
  }

  // query when TLB is empty
  poke(c.io.vaddr, 0x12345678)
  expect(c.io.valid, false)

  // write #1
  poke(c.io.flush, false)
  poke(c.io.vaddr, 0x12345678)
  pokeTlbEntry(0x12345)
  step(1)

  // query for the last write
  poke(c.io.vaddr, 0x12345678)
  expectTlbEntry(0x12345)

  // query for another vaddr
  poke(c.io.vaddr, 0x12346678)
  expect(c.io.valid, false)

  // write #2
  poke(c.io.flush, false)
  poke(c.io.vaddr, 0x12346678)
  pokeTlbEntry(0x67890)
  step(1)

  // query for the last write
  poke(c.io.vaddr, 0x12346678)
  expectTlbEntry(0x67890)

  // query for write #1
  poke(c.io.vaddr, 0x12345678)
  expectTlbEntry(0x12345)

  // write (size - 1) times
  for (i <- 0 until c.size - 1) {
    poke(c.io.flush, false)
    poke(c.io.vaddr, 0x98765430 + i * 4096)
    pokeTlbEntry(0x98000 + i)
    step(1)
  }

  // query for the last (size - 1) writes
  for (i <- 0 until c.size - 1) {
    poke(c.io.vaddr, 0x98765430 + i * 4096)
    expectTlbEntry(0x98000 + i)
  }

  // query for write #2
  poke(c.io.vaddr, 0x12346678)
  expectTlbEntry(0x67890)

  // query for write #1
  poke(c.io.vaddr, 0x12345678)
  expect(c.io.valid, false)

  // flush all entries
  poke(c.io.flush, true)
  step(1)
  
  // query for the last (size - 1) writes
  for (i <- 0 until c.size - 1) {
    poke(c.io.vaddr, 0x98765430 + i * 4096)
    expect(c.io.valid, false)
  }
}

object TlbTest extends App {
  if (!Driver.execute(args, () => new TLB(16)) {
    (c) => new TlbTester(c)
  }) sys.exit(1)
}
