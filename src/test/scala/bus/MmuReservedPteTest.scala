package bus

import org.scalatest.funsuite.AnyFunSuite
import utils.{PeekPokeTester, TestDriver}

class MmuReservedPteTester(c: MMU) extends PeekPokeTester(c) {
  poke(c.io.en, true)
  poke(c.io.flush, false)
  poke(c.io.basePpn, 0x100)
  poke(c.io.sum, false)
  poke(c.io.mxr, false)
  poke(c.io.smode, true)
  poke(c.io.lookup, false)
  poke(c.io.write, false)
  poke(c.io.vaddr, 0x00400100)
  poke(c.io.data.valid, false)
  poke(c.io.data.fault, false)
  poke(c.io.data.accessFault, false)
  poke(c.io.data.rdata, 0)

  def respond(pte: BigInt): Unit = {
    expect(c.io.data.en, true)
    poke(c.io.data.rdata, pte)
    poke(c.io.data.valid, true)
    step(1)
    poke(c.io.data.valid, false)
    step(1)
  }

  // D/A/U must fault individually and in combination. G and RSW remain legal.
  for (flags <- Seq(0x01, 0x21, 0x101, 0x201, 0x301, 0x11, 0x41, 0x81, 0xd1);
       write <- (if (c.isInst) Seq(false) else Seq(false, true))) {
    poke(c.io.lookup, false)
    poke(c.io.flush, true)
    step(1)
    poke(c.io.flush, false)
    step(1)
    poke(c.io.write, write)
    poke(c.io.lookup, true)
    step(1)
    expect(c.io.data.addr, 0x100004)
    respond((BigInt(0x200) << 10) | flags)
    val reserved = (flags & 0xd0) != 0
    if (reserved) {
      expect(c.io.data.en, false) // Do not fetch through an invalid pointer.
    } else {
      expect(c.io.data.addr, 0x200000)
      respond((BigInt(0x80000) << 10) | 0xcf)
    }
    step(1)
    expect(c.io.valid, true)
    expect(c.io.fault, reserved)
    expect(c.io.accessFault, false)
    if (!reserved) expect(c.io.paddr, BigInt("80000100", 16))
  }
}

class MmuReservedPteTest extends AnyFunSuite {
  test("fault on non-leaf D/A/U while accepting global and software bits") {
    for (isInst <- Seq(false, true))
      TestDriver.run(new MMU(16, isInst))(c => new MmuReservedPteTester(c))
  }
}
