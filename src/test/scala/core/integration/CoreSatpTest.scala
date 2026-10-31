package core.integration

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import utils.{AxiTestSupport, CoreTester, ExpectedTrap, ExpectedWriteback,
              InstEncoding, InstructionImage, TestDriver, TrapChecker, WritebackChecker}

// Model Linux's first SATP transition: the physical PC becomes unmapped,
// and STVEC points to the virtual alias of the instruction after CSRW.
private[core] class CoreSatpTester(c: CoreFetchFaultWrapper)
    extends CoreTester(c) with AxiTestSupport {
  import InstEncoding._
  val memory = new InstructionImage
  val boot = Seq(
    lui(5, 1), csrw(0x302, 5), // Delegate instruction page faults to S.
    addi(5, 0, 0x460), csrw(0x341, 5),
    addi(5, 0, 1), slli(5, 5, 11), csrw(0x300, 5), mret())
  boot.zipWithIndex.foreach { case (insn, i) => memory.place32(0x200 + i * 4, insn) }
  val supervisor = Seq(
    lui(5, 0x400), addi(5, 5, 0x488), csrw(0x105, 5),
    lui(10, 0x80000), addi(10, 10, 0x10), nop(), nop(), nop(),
    sfence_vma(), csrw(0x180, 10), // Physical PC 0x484.
    auipc(5, 0), addi(5, 5, 0x14), csrw(0x105, 5),
    csrr(11, 0x141), addi(31, 0, 1), jal(0, 0))
  supervisor.zipWithIndex.foreach { case (insn, i) => memory.place32(0x460 + i * 4, insn) }
  // Map the 4 MiB virtual superpage at 0x400000 to physical zero. The
  // original physical PC has no mapping after SATP becomes active.
  memory.place32(0x10004, 0x4b) // V,R,X,A; S-mode, level-one leaf.
  val traps = new TrapChecker(Seq(ExpectedTrap(0x488, 12, 0x488)))
  val writes = new WritebackChecker(Seq(
    ExpectedWriteback(0x400488, 5, 0x400488),
    ExpectedWriteback(0x40048c, 5, 0x40049c),
    ExpectedWriteback(0x400494, 11, 0x488),
    ExpectedWriteback(0x400498, 31, 1)))
  val ports = Seq(c.io.inst, c.io.data, c.io.uncached)
  ports.foreach(idleAxi)
  val responders = ports.map(port => new AxiReadResponder(port, memory.read32))
  var cycle = 0
  var satpRetired = 0
  runUntil(2500, s"SATP transition; ${writes.missing}; ${traps.missing}") {
    responders.foreach(_.beforeStep(allowAddress = cycle % 3 != 0, allowData = cycle % 5 != 1))
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val wb = sampleDebug(c.io.observation.debug)
      assert(wb.pc < 0x488 || wb.pc >= 0x400000,
        f"Instruction at physical PC 0x${wb.pc}%x retired after SATP enabled translation")
      if (wb.pc == 0x484) satpRetired += 1
      writes.observe(wb)
      done = wb.pc == 0x400498
    }
    step(1)
    responders.foreach(_.afterStep())
    cycle += 1
    done
  }
  writes.checkComplete()
  traps.checkComplete()
  assert(satpRetired == 1, "SATP must commit exactly once while younger instructions are flushed")
}

class CoreSatpTest extends AnyFunSuite {
  test("apply address translation changes") {
    TestDriver.run(new CoreFetchFaultWrapper) { c =>
      new CoreSatpTester(c)
    }
  }
}
