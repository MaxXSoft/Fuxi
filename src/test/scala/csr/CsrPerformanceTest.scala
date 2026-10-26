package csr

import chisel3._
import consts.Parameters.HPM_COUNT
import consts.CSR._
import consts.CsrOp._
import consts.PerfEvent
import utils.{CsrRegressionTester, TestDriver}

class CsrPerformanceTester(c: CsrFile) extends CsrRegressionTester(c) {
  val mask = BigInt("7fd", 16)
  def counter(n: Int, value: BigInt): Unit = {
    read(mhpmCounterAddr(n), value & 0xffffffffL)
    read(hpmCounterAddr(n), value & 0xffffffffL)
    read(mhpmCounterHighAddr(n), value >> 32)
    read(hpmCounterHighAddr(n), value >> 32)
  }
  resetCsr()
  read(CSR_MCOUNTINHIBIT, 0)
  write(CSR_MCOUNTINHIBIT, 0xffffffffL)
  read(CSR_MCOUNTINHIBIT, mask)
  read(CSR_MCYCLE, 1) // The freezing write uses the old CY inhibit bit.
  poke(c.io.write.retired, true)
  poke(c.io.perfEvents, ((BigInt(1) << PerfEvent.values.size) - 1))
  step(5)
  read(CSR_MCYCLE, 1)
  read(CSR_MINSTRET, 0)
  for (n <- 3 to 31) {
    counter(n, 0)
    read(mhpmEventAddr(n), 0)
  }
  for (n <- 11 to 31) {
    write(mhpmCounterAddr(n), 0xffffffffL)
    write(mhpmCounterHighAddr(n), 0xffffffffL)
    write(mhpmEventAddr(n), 1)
    counter(n, 0)
    read(mhpmEventAddr(n), 0)
  }
  write(mhpmCounterAddr(3), 0xfffffffeL)
  write(mhpmCounterAddr(4), 7)
  write(mhpmEventAddr(3), 1)
  write(mhpmEventAddr(4), 2)
  poke(c.io.perfEvents, 2)
  write(CSR_MCOUNTINHIBIT, mask & ~BigInt(8))
  counter(3, 0xfffffffeL) // Enabling takes effect after this write edge.
  step(1)
  counter(3, 0xffffffffL)
  step(1)
  counter(3, BigInt(1) << 32)
  counter(4, 7)

  // Each explicit half write wins over a simultaneous selected event, and
  // preserves the other half of the old counter rather than its increment.
  write(mhpmCounterAddr(3), 0x12345678)
  counter(3, BigInt("112345678", 16))
  write(mhpmCounterHighAddr(3), 2)
  counter(3, BigInt("212345678", 16))
  write(mhpmEventAddr(3), 2)
  counter(3, BigInt("212345679", 16)) // Old selector counted at write edge.
  step(3)
  counter(3, BigInt("212345679", 16))
  poke(c.io.perfEvents, 4)
  step(2)
  counter(3, BigInt("21234567b", 16))
  write(mhpmEventAddr(3), 0)
  counter(3, BigInt("21234567c", 16))
  poke(c.io.perfEvents, 1) // Bit zero never counts, even when asserted.
  step(4)
  counter(3, BigInt("21234567c", 16))
  for (invalid <- Seq(BigInt(PerfEvent.values.size), BigInt(64), BigInt(0x10001), BigInt(0xffffffffL))) {
    write(mhpmEventAddr(3), invalid)
    read(mhpmEventAddr(3), 0)
  }
  // Highest supported ID is selectable; nearby unsupported IDs cannot alias
  // it by truncation to the selector's six stored bits.
  write(mhpmEventAddr(3), PerfEvent.values.size - 1)
  poke(c.io.perfEvents, BigInt(1) << (PerfEvent.values.size - 1))
  step(1)
  counter(3, BigInt("21234567d", 16))
  write(CSR_MCOUNTINHIBIT, 8, CSR_RS)
  counter(3, BigInt("21234567e", 16))
  step(3)
  counter(3, BigInt("21234567e", 16))
  write(mhpmCounterAddr(3), 0xffffffffL)
  write(mhpmCounterHighAddr(3), 0xffffffffL)
  write(CSR_MCOUNTINHIBIT, 8, CSR_RC)
  counter(3, BigInt("ffffffffffffffff", 16))
  write(mhpmCounterHighAddr(3), 7)
  counter(3, BigInt("7ffffffff", 16)) // Suppress simultaneous low-half carry.
  write(mhpmCounterAddr(3), 9)
  counter(3, BigInt("700000009", 16)) // Suppress simultaneous high-half carry.
  step(1)
  counter(3, BigInt("70000000a", 16))

  resetCsr()
  write(CSR_MCOUNTINHIBIT, mask)
  write(CSR_MCYCLE, 0xffffffffL)
  write(CSR_MCYCLEH, 0)
  write(CSR_MINSTRET, 0xffffffffL)
  write(CSR_MINSTRETH, 0)
  poke(c.io.write.retired, true)
  write(CSR_MCOUNTINHIBIT, mask & ~BigInt(5))
  read(CSR_MCYCLE, 0xffffffffL)
  read(CSR_MINSTRET, 0xffffffffL)
  step(1)
  read(CSR_MCYCLE, 0)
  read(CSR_MCYCLEH, 1)
  read(CSR_MINSTRET, 0)
  read(CSR_MINSTRETH, 1)
  write(CSR_MINSTRETH, 9)
  read(CSR_MINSTRET, 0)
  read(CSR_MINSTRETH, 9)
  write(CSR_MINSTRET, 7)
  read(CSR_MINSTRET, 7)
  read(CSR_MINSTRETH, 9)
  write(CSR_MCOUNTINHIBIT, mask)
  read(CSR_MINSTRET, 8)
  poke(c.io.read.addr, CSR_MCYCLE)
  val cycle = peek(c.io.read.data)
  step(5)
  read(CSR_MCYCLE, cycle)
  read(CSR_MINSTRET, 8)
  write(CSR_MCOUNTINHIBIT, 5, CSR_RC)
  read(CSR_MINSTRET, 8)
  step(1)
  read(CSR_MCYCLE, cycle + 1)
  read(CSR_MINSTRET, 9)
}

class CsrPerformanceConfigurationTester(c: CsrFile) extends CsrRegressionTester(c) {
  resetCsr()
  val mask = ((BigInt(1) << (c.hpmCount + 3)) - 1) & ~BigInt(2)
  for (addr <- Seq(CSR_MCOUNTEREN, CSR_SCOUNTEREN, CSR_MCOUNTINHIBIT)) {
    write(addr, 0xffffffffL)
    read(addr, mask)
  }
  val last = if (c.hpmCount == 0) 3 else c.hpmCount + 2
  write(mhpmCounterAddr(last), 0xffffffffL)
  write(mhpmCounterHighAddr(last), 0xffffffffL)
  write(mhpmEventAddr(last), 1)
  read(mhpmCounterAddr(last), if (c.hpmCount == 0) 0 else 0xffffffffL)
  read(mhpmCounterHighAddr(last), if (c.hpmCount == 0) 0 else 0xffffffffL)
  read(mhpmEventAddr(last), if (c.hpmCount == 0) 0 else 1)
  poke(c.io.perfEvents, 2)
  write(CSR_MCOUNTINHIBIT, mask & ~(BigInt(1) << last))
  step(1)
  // Full 64-bit wrap on the last implemented slot, or the absent slot stays 0.
  read(mhpmCounterAddr(last), 0)
  read(mhpmCounterHighAddr(last), 0)
}

object CsrPerformanceTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile(HPM_COUNT))(c => new CsrPerformanceTester(c))) sys.exit(1)
  for (count <- Seq(0, 29)) {
    if (!TestDriver.execute(args, () => new CsrFile(count))(
      c => new CsrPerformanceConfigurationTester(c))) sys.exit(1)
  }
}
