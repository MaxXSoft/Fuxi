package sim

import consts.Instructions.NOP
import consts.Parameters.RESET_PC
import utils.{PeekPokeTester, TestDriver}

class RomUnitTester(c: ROM) extends PeekPokeTester(c) {
  poke(c.io.en, true)
  poke(c.io.wen, 0)
  poke(c.io.wdata, 0)
  poke(c.io.addr, RESET_PC.litValue)
  expect(c.io.rdata, NOP)
  step(1)
  expect(c.io.rdata, BigInt("89abcdef", 16))

  poke(c.io.addr, RESET_PC.litValue + 4)
  expect(c.io.rdata, BigInt("89abcdef", 16)) // address changes must not bypass the register
  step(1)
  expect(c.io.rdata, BigInt("01234567", 16))

  for (index <- Seq(2, ROM.DEPTH - 1)) {
    poke(c.io.addr, RESET_PC.litValue + 4 * index)
    step(1)
    expect(c.io.rdata, NOP)
  }

  poke(c.io.en, false)
  step(1)
  expect(c.io.rdata, 0)
  poke(c.io.en, true)
  poke(c.io.addr, RESET_PC.litValue)
  step(1)
  expect(c.io.rdata, BigInt("89abcdef", 16))
  poke(c.reset, 1)
  step(1)
  expect(c.io.rdata, NOP)
  poke(c.reset, 0)
  step(1)
  expect(c.io.rdata, BigInt("89abcdef", 16))
  expect(c.io.valid, true)
  expect(c.io.fault, false)
  expect(c.io.accessFault, false)
}

object RomTest extends App {
  val words = Seq(BigInt("89abcdef", 16), BigInt("01234567", 16))
  if (!TestDriver.execute(args, () => new ROM(words))(c => new RomUnitTester(c))) sys.exit(1)
}
