package bus

import utils.{PeekPokeTester, TestDriver}

class MmuUnitTester(c: MMU) extends PeekPokeTester(c) {
  poke(c.io.en, true)
  poke(c.io.flush, false)
  poke(c.io.basePpn, 0x100)
  poke(c.io.sum, false)
  poke(c.io.smode, false)
  poke(c.io.lookup, true)
  poke(c.io.write, false)
  poke(c.io.vaddr, 0x4000)
  poke(c.io.data.valid, false)
  poke(c.io.data.fault, false)
  poke(c.io.data.accessFault, false)
  poke(c.io.data.rdata, 0)

  // An empty TLB starts a page-table memory request.
  step(1)
  expect(c.io.data.en, true)
  expect(c.io.accessFault, false)

  // A physical error while fetching the PTE aborts the walk and is reported
  // to the original lookup instead of interpreting the response as a PTE.
  poke(c.io.data.valid, true)
  poke(c.io.data.accessFault, true)
  step(1)
  expect(c.io.accessFault, true)
  expect(c.io.fault, false)

  poke(c.io.lookup, false)
  poke(c.io.data.valid, false)
  poke(c.io.data.accessFault, false)
  step(1)
  expect(c.io.accessFault, false)
}

class MmuRedirectTester(c: MMU) extends PeekPokeTester(c) {
  val rootPpn = BigInt(0x100)
  val tablePpn = BigInt(0x200)
  // Both VPN levels and the offsets differ, so mixing either VPN is visible.
  val original = BigInt("00403124", 16)
  val redirected = BigInt("00c09568", 16)
  val third = BigInt("0140babc", 16)
  val leafPpn = BigInt("80012", 16)
  val superPpn = BigInt("80400", 16)

  poke(c.io.en, true)
  poke(c.io.flush, false)
  poke(c.io.basePpn, rootPpn)
  poke(c.io.sum, false)
  poke(c.io.smode, true)
  poke(c.io.lookup, false)
  poke(c.io.write, false)
  poke(c.io.vaddr, original)
  poke(c.io.data.valid, false)
  poke(c.io.data.fault, false)
  poke(c.io.data.accessFault, false)
  poke(c.io.data.rdata, 0)

  def pteAddress(ppn: BigInt, vaddr: BigInt, root: Boolean): BigInt =
    (ppn << 12) + (((vaddr >> (if (root) 22 else 12)) & 0x3ff) * 4)

  def clearTlb(): Unit = {
    poke(c.io.lookup, false)
    poke(c.io.flush, true)
    step(1)
    poke(c.io.flush, false)
    step(1)
  }

  def expectRequest(address: BigInt, delay: Int): Unit = {
    for (_ <- 0 until delay) {
      expect(c.io.data.en, true)
      expect(c.io.data.addr, address)
      expect(c.io.data.wen, 0)
      expect(c.io.valid, false)
      expect(c.io.accessFault, false)
      step(1)
    }
  }

  def respond(pte: BigInt): Unit = {
    poke(c.io.data.rdata, pte)
    poke(c.io.data.valid, true)
    step(1)
    poke(c.io.data.valid, false)
    // The SRAM response is consumed one cycle after the handshake.
    step(1)
  }

  def expectTranslation(vaddr: BigInt, paddr: BigInt, fault: Boolean): Unit = {
    poke(c.io.vaddr, vaddr)
    expect(c.io.valid, true)
    expect(c.io.fault, fault)
    expect(c.io.accessFault, false)
    if (!fault) expect(c.io.paddr, paddr)
  }

  // Redirect during the root wait, the leaf wait, or immediately before the
  // TLB fill. All three must keep the old walk separate from the new lookup.
  for (phase <- Seq("root", "leaf", "fill")) {
    clearTlb()
    poke(c.io.vaddr, original)
    poke(c.io.lookup, true)
    step(1)
    if (phase == "root") poke(c.io.vaddr, redirected)
    expectRequest(pteAddress(rootPpn, original, true), 3)
    respond((tablePpn << 10) | 1) // non-leaf, V only

    if (phase == "leaf") poke(c.io.vaddr, redirected)
    expectRequest(pteAddress(tablePpn, original, false), 4)
    respond((leafPpn << 10) | 0xcf) // supervisor RWX leaf with A/D set

    if (phase == "fill") poke(c.io.vaddr, redirected)
    step(1)
    expect(c.io.valid, false) // the old PTE must not hit the redirected VPN
    poke(c.io.lookup, false)
    expectTranslation(original, (leafPpn << 12) | (original & 0xfff), false)

    // Offset selection still follows the live lookup, even on a cached hit.
    expectTranslation(original + 4, (leafPpn << 12) | ((original + 4) & 0xfff), false)
    poke(c.io.vaddr, third)
    expect(c.io.valid, false)

    // The redirected address must perform its own root lookup and translate
    // independently; completing it must preserve the first cached mapping.
    poke(c.io.vaddr, redirected)
    poke(c.io.lookup, true)
    step(1)
    expectRequest(pteAddress(rootPpn, redirected, true), 2)
    respond((superPpn << 10) | 0xcf)
    step(1)
    poke(c.io.lookup, false)
    expectTranslation(redirected, (superPpn << 12) | (redirected & 0x3fffff), false)
    expectTranslation(original, (leafPpn << 12) | (original & 0xfff), false)
  }

  // A root-level leaf obtains its low PPN from the original VPN, even if the
  // input changes both while waiting and on the TLB-write cycle.
  clearTlb()
  poke(c.io.vaddr, original)
  poke(c.io.lookup, true)
  step(1)
  poke(c.io.vaddr, redirected)
  expectRequest(pteAddress(rootPpn, original, true), 3)
  respond((superPpn << 10) | 0xcf)
  poke(c.io.vaddr, third)
  step(1)
  poke(c.io.lookup, false)
  expect(c.io.valid, false)
  poke(c.io.vaddr, redirected)
  expect(c.io.valid, false)
  expectTranslation(original, (superPpn << 12) | (original & 0x3fffff), false)

  // Fault entries also belong to the walk's original VPN. A discarded fetch
  // must not install its page fault under the newly requested address.
  clearTlb()
  poke(c.io.vaddr, original)
  poke(c.io.lookup, true)
  step(1)
  poke(c.io.vaddr, redirected)
  expectRequest(pteAddress(rootPpn, original, true), 2)
  respond(0)
  step(1)
  poke(c.io.lookup, false)
  expect(c.io.valid, false)
  expect(c.io.fault, false)
  expectTranslation(original, 0, true)
}

class MmuPrivilegeTester(c: MMU) extends PeekPokeTester(c) {
  poke(c.io.en, true)
  poke(c.io.flush, false)
  poke(c.io.basePpn, 0x100)
  poke(c.io.sum, false)
  poke(c.io.smode, true)
  poke(c.io.lookup, false)
  poke(c.io.write, false)
  poke(c.io.vaddr, 0x00400100)
  poke(c.io.data.valid, false)
  poke(c.io.data.fault, false)
  poke(c.io.data.accessFault, false)
  poke(c.io.data.rdata, 0)

  for (userPage <- Seq(false, true)) {
    poke(c.io.flush, true)
    step(1)
    poke(c.io.flush, false)
    step(1)
    poke(c.io.lookup, true)
    step(1)
    expect(c.io.data.en, true)
    // Aligned 4 MiB RWX leaf with A/D set; vary only the U permission.
    poke(c.io.data.rdata, (BigInt(0x80000) << 10) | 0xcf |
      (if (userPage) 0x10 else 0))
    poke(c.io.data.valid, true)
    step(1)
    poke(c.io.data.valid, false)
    step(2)
    poke(c.io.lookup, false)

    // A privilege/SUM change must take effect even for an existing TLB hit.
    for (supervisor <- Seq(false, true); sum <- Seq(false, true);
         write <- (if (c.isInst) Seq(false) else Seq(false, true))) {
      poke(c.io.smode, supervisor)
      poke(c.io.sum, sum)
      poke(c.io.write, write)
      expect(c.io.valid, true)
      expect(c.io.fault, if (supervisor) userPage && (c.isInst || !sum) else !userPage)
      expect(c.io.accessFault, false)
    }
    // Bare mode must not apply permissions from a cached translation.
    poke(c.io.en, false)
    expect(c.io.valid, true)
    expect(c.io.fault, false)
    expect(c.io.paddr, 0x00400100)
    poke(c.io.en, true)
  }
}

class MmuFlushTester(c: MMU) extends PeekPokeTester(c) {
  val vaddr = BigInt("00400100", 16)
  val rootAddress = BigInt(0x100004)
  val oldPte = (BigInt(0x80000) << 10) | 0xcf
  val newPte = (BigInt(0x80400) << 10) | 0xcf
  poke(c.io.en, true)
  poke(c.io.flush, false)
  poke(c.io.basePpn, 0x100)
  poke(c.io.sum, false)
  poke(c.io.smode, true)
  poke(c.io.lookup, false)
  poke(c.io.write, false)
  poke(c.io.vaddr, vaddr)
  poke(c.io.data.valid, false)
  poke(c.io.data.fault, false)
  poke(c.io.data.accessFault, false)
  poke(c.io.data.rdata, oldPte)

  for (phase <- Seq("address", "read", "update");
       responseError <- (if (phase == "address") Seq(false, true) else Seq(false))) {
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
    poke(c.io.lookup, true)
    poke(c.io.data.rdata, oldPte)
    step(1)
    poke(c.io.lookup, false)
    expect(c.io.data.en, true)
    expect(c.io.data.addr, rootAddress)
    if (phase == "address") {
      poke(c.io.flush, true)
      step(1)
      poke(c.io.flush, false)
      for (_ <- 0 until 3) {
        expect(c.io.data.en, true) // accepted lower-level work must be drained
        expect(c.io.data.addr, rootAddress)
        step(1)
      }
    }
    poke(c.io.data.valid, true)
    poke(c.io.data.accessFault, responseError)
    step(1)
    poke(c.io.data.valid, false)
    poke(c.io.data.accessFault, false)
    if (phase == "update") step(1)
    if (phase != "address") {
      poke(c.io.flush, true)
      step(1)
      poke(c.io.flush, false)
    }
    for (_ <- 0 until 3) {
      expect(c.io.accessFault, false)
      step(1)
    }
    expect(c.io.valid, false)
    expect(c.io.fault, false)

    // A replacement PTE must be fetched rather than reusing the old result.
    poke(c.io.lookup, true)
    step(1)
    expect(c.io.data.en, true)
    expect(c.io.data.addr, rootAddress)
    poke(c.io.data.rdata, newPte)
    poke(c.io.data.valid, true)
    step(1)
    poke(c.io.data.valid, false)
    step(2)
    poke(c.io.lookup, false)
    expect(c.io.valid, true)
    expect(c.io.fault, false)
    expect(c.io.paddr, BigInt("80400100", 16))
  }
}

object MmuTest extends App {
  if (!TestDriver.execute(args, () => new MMU(16, false)) {
    (c) => new MmuUnitTester(c)
  }) sys.exit(1)
  for (isInst <- Seq(true, false)) {
    if (!TestDriver.execute(args, () => new MMU(16, isInst)) {
      (c) => new MmuRedirectTester(c)
    }) sys.exit(1)
    if (!TestDriver.execute(args, () => new MMU(16, isInst)) {
      (c) => new MmuPrivilegeTester(c)
    }) sys.exit(1)
    if (!TestDriver.execute(args, () => new MMU(16, isInst)) {
      (c) => new MmuFlushTester(c)
    }) sys.exit(1)
  }
}
