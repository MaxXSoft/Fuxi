package core.integration

import chisel3._
import consts.CSR._
import consts.PerfEvent._
import sim.ROM
import utils.InstEncoding._
import utils.{CoreMemoryHarness, CoreProgram, CoreProgramTester, CoreTester, CoreWrapper, TestDriver}

object HpmReadProgram extends CoreProgram {
  val allInhibited = 0x7fd
  def write(addr: UInt, value: Int): Unit = {
    emit32(addi(5, 0, value))
    emit32(csrw(addr.litValue.toInt, 5))
  }
  def read(addr: UInt, value: BigInt): Unit = {
    expectWriteback(6, value)
    emit32(csrr(6, addr.litValue.toInt))
  }
  def branch(): Unit = emit32(bne(0, 0, 4))

  write(CSR_MCOUNTINHIBIT, allInhibited)
  write(CSR_MHPMEVENT(3), BRANCH.id)
  write(CSR_MHPMEVENT(4), COMPRESSED.id)
  write(CSR_MHPMCOUNTERH(3), 0)
  write(CSR_MHPMCOUNTER(3), -1)
  read(CSR_HPMCOUNTERH(3), 0)
  read(CSR_HPMCOUNTER(3), 0xffffffffL)
  write(CSR_MCOUNTINHIBIT, allInhibited & ~24)
  branch()
  // The immediately preceding branch must reach retirement before either
  // counter half is sampled; this first event carries into the high half.
  read(CSR_HPMCOUNTERH(3), 1)
  read(CSR_MHPMCOUNTER(3), 0)
  branch()
  read(CSR_HPMCOUNTER(3), 1)
  for (_ <- 0 until 3) emit16(c_nop())
  read(CSR_HPMCOUNTER(4), 3)

  branch()
  emit32(csrrsi(0, CSR_MCOUNTINHIBIT.litValue.toInt, 8))
  branch()
  read(CSR_HPMCOUNTER(3), 2)
  emit16(c_nop())
  read(CSR_HPMCOUNTER(4), 4)
  // Reprogram an active counter. Following compressed instructions no longer
  // count, and the subsequent retired conditional branch increments it once.
  write(CSR_MHPMEVENT(4), BRANCH.id)
  emit16(c_nop())
  branch()
  read(CSR_MHPMCOUNTER(4), 5)
  write(CSR_MHPMCOUNTERH(4), 3)
  read(CSR_HPMCOUNTERH(4), 3)
  write(CSR_MHPMCOUNTER(4), 7)
  read(CSR_HPMCOUNTER(4), 7)
  emit32(addi(5, 0, 16))
  expectWriteback(6, 7)
  emit32(csrrs(6, CSR_MHPMCOUNTER(4).litValue.toInt, 5))
  read(CSR_HPMCOUNTER(4), 23)
  expectWriteback(6, 23)
  emit32(csrrc(6, CSR_MHPMCOUNTER(4).litValue.toInt, 5))
  read(CSR_MHPMCOUNTER(4), 7)
  emit32(addi(5, 0, 8))
  emit32(csrrc(0, CSR_MCOUNTINHIBIT.litValue.toInt, 5))
  branch()
  read(CSR_HPMCOUNTER(3), 3)
  read(CSR_HPMCOUNTER(4), 8)
  write(CSR_MCOUNTINHIBIT, allInhibited)

  // Exercise the enabled user aliases through the real mret/CSR path.
  write(CSR_MCOUNTEREN, 8)
  write(CSR_SCOUNTEREN, 8)
  write(CSR_MSTATUS, 0)
  val userPc = pc + 12 // Two instructions to set mepc, then mret.
  write(CSR_MEPC, userPc.toInt)
  emit32(mret())
  read(CSR_HPMCOUNTER(3), 3)
  read(CSR_HPMCOUNTERH(3), 1)
  val donePc = finish()
}

object CoreHpmReadTest extends App {
  if (!TestDriver.execute(args, () => new CoreWrapper(HpmReadProgram.words))(
    c => new CoreProgramTester(c, HpmReadProgram, HpmReadProgram.donePc, 2000))) sys.exit(1)
  if (!TestDriver.execute(args, () => new CoreMemoryHarness(ROM.Words(CycleRmwProgram.words)))(
    c => new CycleRmwTester(c))) sys.exit(1)
}

// A zero-valued source register other than x0 requests a real set/clear write.
// It exposes any mismatch between rd's EX snapshot and the RMW's input value.
object CycleRmwProgram extends CoreProgram {
  emit32(addi(5, 0, 0))
  emit32(addi(7, 0, 0x7fd))
  emit32(addi(9, 0, 0x7fc))
  emit32(csrw(CSR_MCOUNTINHIBIT.litValue.toInt, 7))
  emit32(csrw(CSR_MCYCLE.litValue.toInt, 5))
  val sequences = for (op <- Seq(2, 3)) yield {
    emit32(csrw(CSR_MCOUNTINHIBIT.litValue.toInt, 9))
    val rmwPc = pc
    emit32(if (op == 2) csrrs(6, CSR_MCYCLE.litValue.toInt, 5)
           else csrrc(6, CSR_MCYCLE.litValue.toInt, 5))
    val freezePc = pc
    emit32(csrw(CSR_MCOUNTINHIBIT.litValue.toInt, 7))
    val readPc = pc
    emit32(csrr(8, CSR_MCYCLE.litValue.toInt))
    (rmwPc, freezePc, readPc)
  }
  val donePc = finish()
}

class CycleRmwTester(c: CoreMemoryHarness) extends CoreTester(c) {
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)
  var snapshot = BigInt(0)
  var writeCycle = 0L
  var expected = BigInt(0)
  var checked = 0
  runUntil(1000, s"Only checked $checked cycle RMW snapshots") {
    val retired = peek(c.io.observation.retired) != 0
    val pc = peek(c.io.observation.debug.pc)
    if (retired) {
      for ((rmwPc, freezePc, readPc) <- CycleRmwProgram.sequences) {
        if (pc == rmwPc) {
          snapshot = peek(c.io.observation.debug.regWdata)
          writeCycle = t
        }
        if (pc == freezePc) {
          // Explicit RMW writes the EX snapshot at its WB edge. CY remains
          // active through the freezing edge, regardless of intervening stalls.
          expected = (snapshot + (t - writeCycle)) & 0xffffffffL
        }
        if (pc == readPc) {
          expect(c.io.observation.debug.regWdata, expected)
          checked += 1
        }
      }
    }
    val done = retired && pc == CycleRmwProgram.donePc
    step(1)
    done
  }
  assert(checked == 2)
}
