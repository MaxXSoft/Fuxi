package csr

import chisel3._
import consts.Parameters.HPM_COUNT
import consts.CSR._
import consts.CsrOp._
import consts.PerfEvent
import consts.PerfEvent.{Group, Retired => R, Memory => M}
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
  c.io.perfEvents.groups.foreach(_.foreach(poke(_, true)))
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
    write(mhpmEventAddr(n), 0x01000000)
    counter(n, 0)
    read(mhpmEventAddr(n), 0)
  }
  write(mhpmCounterAddr(3), 0xfffffffeL)
  write(mhpmCounterAddr(4), 7)
  write(mhpmEventAddr(3), 0x01000000)
  write(mhpmEventAddr(4), 0x01000001)
  driveEvent(Group.RETIRED, R.BRANCH)
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
  write(mhpmEventAddr(3), 0x01000001)
  counter(3, BigInt("212345679", 16)) // Old selector counted at write edge.
  step(3)
  counter(3, BigInt("212345679", 16))
  driveEvent(Group.RETIRED, R.BRANCH_MISS)
  step(2)
  counter(3, BigInt("21234567b", 16))
  write(mhpmEventAddr(3), 0)
  counter(3, BigInt("21234567c", 16))
  driveEvent(Group.RETIRED, R.BRANCH) // Group zero disables even local event zero.
  step(4)
  counter(3, BigInt("21234567c", 16))
  for (invalid <- Seq(BigInt(1), BigInt(0x00ffffff), BigInt(0x04000000),
                       BigInt(0xff000000L), BigInt(0xffffffffL)) ++
                  PerfEvent.groups.flatMap { case (group, events) =>
                    Seq((BigInt(group.id) << 24) | events.values.size,
                        (BigInt(group.id) << 24) | 0x100,
                        (BigInt(group.id) << 24) | 0xffffff)
                  }) {
    write(mhpmEventAddr(3), invalid)
    read(mhpmEventAddr(3), 0)
  }
  // The last event of the last group is selectable, independently of the
  // number of implemented counters. Upper local-ID bits must not alias it.
  write(mhpmEventAddr(3), 0x0300000e)
  driveEvent(Group.MEMORY, M.DTLB_WALK_CYCLE)
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
  for ((selectedGroup, selectedEvents) <- PerfEvent.groups;
       selectedEvent <- selectedEvents.values) {
    clearEvents()
    write(CSR_MCOUNTINHIBIT, mask)
    write(mhpmCounterAddr(3), 0)
    val encoded = PerfEvent.encode(selectedGroup, selectedEvent)
    write(mhpmEventAddr(3), encoded)
    read(mhpmEventAddr(3), encoded)
    write(CSR_MCOUNTINHIBIT, mask & ~BigInt(8))
    var expected = BigInt(0)
    for ((group, events) <- PerfEvent.groups; event <- events.values) {
      driveEvent(group, event)
      step(1)
      if (group == selectedGroup && event == selectedEvent) expected += 1
      counter(3, expected)
    }
    assert(expected == 1)
  }

  // Reprogram across groups while active. The write edge uses the old group;
  // local ID zero is a real event in every implemented group.
  resetCsr()
  driveEvent(Group.RETIRED, R.BRANCH)
  write(mhpmEventAddr(3), 0x01000000)
  write(mhpmEventAddr(3), 0x02000000)
  counter(3, 1)
  step(1)
  counter(3, 1)
  driveEvent(Group.MICROARCHITECTURE, PerfEvent.Microarchitecture.FRONTEND_EMPTY)
  step(1)
  counter(3, 2)
  // CSRRS that produces an unsupported local ID disables the entire selector.
  write(mhpmEventAddr(3), 0x100, CSR_RS)
  read(mhpmEventAddr(3), 0)
  counter(3, 3)
  c.io.perfEvents.groups.foreach(_.foreach(poke(_, true)))
  step(2)
  counter(3, 3)
  write(mhpmEventAddr(3), 0x03000001)
  write(mhpmEventAddr(3), 0x03000000, CSR_RC)
  read(mhpmEventAddr(3), 0) // Clearing the group cannot retain a local-only ID.
  counter(3, 4)
  step(2)
  counter(3, 4)

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
    read(addr, if (addr.litValue == CSR_MCOUNTINHIBIT.litValue) mask else mask | 2)
  }
  val last = if (c.hpmCount == 0) 3 else c.hpmCount + 2
  write(mhpmCounterAddr(last), 0xffffffffL)
  write(mhpmCounterHighAddr(last), 0xffffffffL)
  write(mhpmEventAddr(last), 0x01000000)
  read(mhpmCounterAddr(last), if (c.hpmCount == 0) 0 else 0xffffffffL)
  read(mhpmCounterHighAddr(last), if (c.hpmCount == 0) 0 else 0xffffffffL)
  read(mhpmEventAddr(last), if (c.hpmCount == 0) 0 else 0x01000000)
  driveEvent(Group.RETIRED, R.BRANCH)
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
