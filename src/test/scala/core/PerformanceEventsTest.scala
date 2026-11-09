package core

import org.scalatest.funsuite.AnyFunSuite

import chisel3._
import consts.CSR._
import consts.CsrOp._
import consts.PerfEvent
import consts.PerfEvent.{Group, Retired => R}
import csr.CsrFile
import io._
import utils.{PeekPokeTester, TestDriver}

// Exercise the real event producer and CSR consumer together, including writes
// at the sampling/counting boundary. Other event sources are held inactive.
private class HpmEventPipelineHarness extends Module {
  val io = IO(new Bundle {
    val read = Flipped(new CsrReadIO)
    val write = Flipped(new CsrWriteIO)
    val branch = Input(Bool())
    val miss = Input(Bool())
  })
  val perf = Module(new PerformanceEvents)
  val csr = Module(new CsrFile(1, 0))
  perf.io.retired := io.branch
  perf.io.retire := 0.U.asTypeOf(new RetirePerformanceIO)
  perf.io.retire.branch := true.B
  perf.io.retire.miss := io.miss
  perf.io.retire.directionMiss := io.miss
  perf.io.flush := false.B
  perf.io.flushIf := false.B
  perf.io.stallId := false.B
  perf.io.fetchValid := false.B
  perf.io.fetchReady := true.B
  perf.io.decodeValid := true.B
  perf.io.decodeRedirect := false.B
  perf.io.loadHazard := false.B
  perf.io.csrHazard := false.B
  perf.io.aluStall := false.B
  perf.io.memStall := false.B
  perf.io.ras := 0.U.asTypeOf(new RasPerformanceIO)
  perf.io.bus := 0.U.asTypeOf(new BusPerformanceIO)
  csr.io.read <> io.read
  csr.io.write <> io.write
  csr.io.irq := 0.U.asTypeOf(new InterruptIO)
  csr.io.rtcTime := 0.U
  csr.io.except := 0.U.asTypeOf(new ExceptInfoIO)
  csr.io.perfEvents := perf.io.events
}

private class HpmEventDelayTester(c: HpmEventPipelineHarness) extends PeekPokeTester(c) {
  def clearEvents(): Unit = {
    poke(c.io.branch, false)
    poke(c.io.miss, false)
  }
  def driveBranch(miss: Boolean = false): Unit = {
    poke(c.io.branch, true)
    poke(c.io.miss, miss)
  }
  def resetCsr(): Unit = {
    poke(c.io.read.op, CSR_R)
    poke(c.io.read.addr, CSR_MSTATUS)
    poke(c.io.write.op, CSR_NOP)
    poke(c.io.write.addr, 0)
    poke(c.io.write.data, 0)
    poke(c.io.write.retired, false)
    clearEvents()
    poke(c.reset, 1)
    step(1)
    poke(c.reset, 0)
  }
  def write(addr: UInt, data: BigInt): Unit = {
    poke(c.io.write.op, CSR_W)
    poke(c.io.write.addr, addr)
    poke(c.io.write.data, data)
    step(1)
    poke(c.io.write.op, CSR_NOP)
  }
  def read(addr: UInt, data: BigInt): Unit = {
    poke(c.io.read.addr, addr)
    expect(c.io.read.valid, true)
    expect(c.io.read.data, data)
  }

  resetCsr()
  write(mhpmEventAddr(3), PerfEvent.encode(Group.RETIRED, R.BRANCH))
  driveBranch()
  step(1)
  read(mhpmCounterAddr(3), 0)
  clearEvents()
  step(1)
  read(mhpmCounterAddr(3), 1)
  step(2)
  read(mhpmCounterAddr(3), 1) // A one-cycle pulse counts exactly once.

  driveBranch()
  step(3)
  read(mhpmCounterAddr(3), 3)
  clearEvents()
  step(1)
  read(mhpmCounterAddr(3), 4) // Consecutive active cycles are all retained.

  driveBranch()
  write(CSR_MCOUNTINHIBIT, 8)
  clearEvents()
  step(1)
  read(mhpmCounterAddr(3), 4) // Newly inhibited counter drops the pending sample.
  write(CSR_MCOUNTINHIBIT, 0)
  step(1)
  read(mhpmCounterAddr(3), 4) // No replay on unfreeze.

  write(CSR_MCOUNTINHIBIT, 8)
  driveBranch()
  write(CSR_MCOUNTINHIBIT, 0)
  clearEvents()
  step(1)
  read(mhpmCounterAddr(3), 5) // The unfreezing edge's sample counts next cycle.

  driveBranch()
  step(1)
  clearEvents()
  write(mhpmCounterAddr(3), 42)
  step(1)
  read(mhpmCounterAddr(3), 42) // Explicit writes override delayed increments too.

  driveBranch(miss = true)
  write(mhpmEventAddr(3), PerfEvent.encode(Group.RETIRED, R.BRANCH_MISS))
  clearEvents()
  step(1)
  read(mhpmCounterAddr(3), 43) // Select with the controls at the counting edge.

  driveBranch(miss = true)
  step(1)
  resetCsr()
  write(mhpmEventAddr(3), PerfEvent.encode(Group.RETIRED, R.BRANCH_MISS))
  step(1)
  read(mhpmCounterAddr(3), 0) // Reset clears an event still in flight.
}

class PerformanceEventsTest extends AnyFunSuite {
  test("delay HPM events by one cycle without losing or replaying samples") {
    TestDriver.run(new HpmEventPipelineHarness) { c =>
      new HpmEventDelayTester(c)
    }
  }
}
