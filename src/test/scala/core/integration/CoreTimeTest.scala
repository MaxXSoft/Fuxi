package core.integration

import utils.DefaultTestConfig._
import chisel3._
import consts.CSR._
import consts.Architecture.RTC_WIDTH
import sim.ROM
import utils.InstEncoding._
import utils.{CoreMemoryHarness, CoreProgram, CoreTester, TestDriver, TrapChecker, WritebackChecker}

private object TimeProgram extends CoreProgram {
  val samples = Seq("1234567887654321", "12345678ffffffff",
                    "1234567900000000", "1234567900000042").map(BigInt(_, 16))
  val cycleLow = 0x135
  val cycleHigh = 0x246

  // Freeze mcycle at a distinct value to catch an accidental cycle/time alias
  // or application of mcountinhibit.CY to the external RTC.
  emit32(addi(5, 0, 1))
  emit32(csrw(CSR_MCOUNTINHIBIT.litValue.toInt, 5))
  emit32(addi(5, 0, cycleLow))
  emit32(csrw(CSR_MCYCLE.litValue.toInt, 5))
  emit32(addi(5, 0, cycleHigh))
  emit32(csrw(CSR_MCYCLEH.litValue.toInt, 5))

  def read(rd: Int, address: UInt, expected: BigInt): Unit = {
    expectWriteback(rd, expected)
    emit32(csrr(rd, address.litValue.toInt))
  }

  for (value <- samples) {
    // The harness holds this load while changing the external RTC. Following
    // CSR reads sample the new value after the memory stall is released.
    emit32(lw(0, 0, 0))
    read(10, CSR_TIME, value & 0xffffffffL)
    read(11, CSR_TIMEH, value >> 32)
    read(12, CSR_MCYCLE, cycleLow)
    read(13, CSR_MCYCLEH, cycleHigh)
  }
  val donePc = finish()
}

private class TimeHarness extends CoreMemoryHarness(ROM.Words(TimeProgram.words)) {
  val rtcTime = IO(Input(UInt(RTC_WIDTH.W)))
  core.io.rtcTime := rtcTime
}

private class TimeTester(c: TimeHarness) extends CoreTester(c) {
  val writes = new WritebackChecker(TimeProgram.expected)
  val traps = new TrapChecker(Seq.empty)
  var samplesDriven = 0
  var memoryArmed = true
  var memoryWait = 0
  poke(c.rtcTime, 0)
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)
  runUntil(1000, s"Missing RTC writebacks: ${writes.missing}") {
    if (peek(c.io.memoryRequest) == 0) memoryArmed = true
    else if (memoryArmed) {
      assert(samplesDriven < TimeProgram.samples.size, "Unexpected RTC synchronization load")
      poke(c.rtcTime, TimeProgram.samples(samplesDriven))
      samplesDriven += 1
      memoryWait = 3
      memoryArmed = false
    }
    poke(c.io.memoryStall, memoryWait > 0)
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      writes.observe(actual)
      done = actual.pc == TimeProgram.donePc
    }
    step(1)
    memoryWait = math.max(0, memoryWait - 1)
    done
  }
  writes.checkComplete()
  traps.checkComplete()
  assert(samplesDriven == TimeProgram.samples.size, "Not all RTC samples were driven")
}

object CoreTimeTest extends App {
  if (!TestDriver.execute(args, () => new TimeHarness)(c => new TimeTester(c))) sys.exit(1)
}
