package bus

import utils.{PeekPokeTester, TestDriver}

class TlbTester(c: TLB) extends PeekPokeTester(c) {
  poke(c.io.flush, false)
  poke(c.io.wen, false)
  poke(c.io.waddr, 0)
  poke(c.io.went.ppn, 0)
  poke(c.io.went.d, true)
  poke(c.io.went.a, true)
  poke(c.io.went.u, false)
  poke(c.io.went.x, true)
  poke(c.io.went.w, true)
  poke(c.io.went.r, true)

  def writeTlbEntry(vaddr: BigInt, ppn: Int): Unit = {
    poke(c.io.wen, true)
    poke(c.io.waddr, vaddr)
    poke(c.io.went.ppn, ppn)
    step(1)
    poke(c.io.wen, false)
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
  writeTlbEntry(0x12345678, 0x12345)

  // query for the last write
  poke(c.io.vaddr, 0x12345678)
  expectTlbEntry(0x12345)

  // query for another vaddr
  poke(c.io.vaddr, 0x12346678)
  expect(c.io.valid, false)

  // write #2
  poke(c.io.flush, false)
  poke(c.io.vaddr, 0x12346678)
  writeTlbEntry(0x12346678, 0x67890)

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
    writeTlbEntry(BigInt("98765430", 16) + i * 4096, 0x98000 + i)
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

  // A completed walk writes its own VPN while lookup follows a new request.
  poke(c.io.flush, false)
  poke(c.io.vaddr, 0x34567234)
  writeTlbEntry(0x12345100, 0x12345)
  expect(c.io.valid, false)
  poke(c.io.vaddr, 0x12345abc)
  expectTlbEntry(0x12345)

  // Conversely, filling a different VPN must preserve the active lookup hit.
  writeTlbEntry(0x34567100, 0x67890)
  expectTlbEntry(0x12345)
  poke(c.io.vaddr, 0x34567abc)
  expectTlbEntry(0x67890)
}

object TlbTest extends App {
  if (!TestDriver.execute(args, () => new TLB(16)) {
    (c) => new TlbTester(c)
  }) sys.exit(1)
}
