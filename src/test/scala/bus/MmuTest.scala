package bus

import chisel3.iotesters.{Driver, PeekPokeTester}

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

object MmuTest extends App {
  if (!Driver.execute(args, () => new MMU(16, false)) {
    (c) => new MmuUnitTester(c)
  }) sys.exit(1)
}
