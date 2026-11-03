package bus

import org.scalatest.funsuite.AnyFunSuite

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

class TlbTest extends AnyFunSuite {
  test("look up translations and invalidate entries") {
    TestDriver.run(new TLB(16)) {
      (c) => new TlbTester(c)
    }
  }

  test("preserve duplicate priority, replacement and flush across TLB sizes") {
    for (size <- Seq(2, 4, 16)) {
      TestDriver.run(new TLB(size)) { c => new TlbPriorityTester(c) }
    }
  }
}

class TlbPriorityTester(c: TLB) extends PeekPokeTester(c) {
  case class Entry(vpn: Int, ppn: Int, flags: Int)
  val entries = Array.fill[Option[Entry]](c.size)(None)
  var pointer = 0
  val writeFlags = Seq(c.io.went.r, c.io.went.w, c.io.went.x,
                       c.io.went.u, c.io.went.a, c.io.went.d)
  val readFlags = Seq(c.io.rent.r, c.io.rent.w, c.io.rent.x,
                      c.io.rent.u, c.io.rent.a, c.io.rent.d)
  poke(c.io.flush, false)
  poke(c.io.wen, false)
  poke(c.io.waddr, 0)
  poke(c.io.went.ppn, 0)
  writeFlags.foreach(poke(_, false))

  def check(): Unit = {
    for (vpn <- 0 to c.size + 1) {
      poke(c.io.vaddr, (vpn << 12) | 0xabc)
      val expected = entries.flatten.filter(_.vpn == vpn).lastOption
      expect(c.io.valid, expected.nonEmpty)
      expect(c.io.rent.ppn, expected.map(_.ppn).getOrElse(0))
      for ((flag, bit) <- readFlags.zipWithIndex) {
        expect(flag, expected.exists(e => ((e.flags >> bit) & 1) != 0))
      }
    }
  }

  def write(vpn: Int, ppn: Int, flags: Int, flush: Boolean = false): Unit = {
    poke(c.io.wen, true)
    poke(c.io.flush, flush)
    poke(c.io.waddr, vpn << 12)
    poke(c.io.went.ppn, ppn)
    for ((flag, bit) <- writeFlags.zipWithIndex) poke(flag, ((flags >> bit) & 1) != 0)
    step(1)
    if (flush) {
      for (i <- entries.indices) entries(i) = None
    } else {
      entries(pointer) = Some(Entry(vpn, ppn, flags))
      pointer = (pointer + 1) % c.size
    }
    poke(c.io.flush, false)
    poke(c.io.wen, false)
    check()
  }

  check()
  // Fill duplicates, then wrap: the most recent write is not necessarily
  // the highest-index match. Exercise every permission bit independently.
  for (i <- 0 until 2 * c.size) write(1, 0x100 + i, (i * 13) & 63)
  for (i <- 0 until c.size) write(i + 2, 0x200 + i, (i * 7) & 63)
  write(1, 0x300, 63, flush = true)
  for (i <- 0 until c.size) write(1, 0x400 + i, (i * 11) & 63)
}
