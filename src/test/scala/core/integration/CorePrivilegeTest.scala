package core.integration

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import utils.{AxiTestSupport, CoreTester, ExpectedTrap, ExpectedWriteback,
              InstEncoding, InstructionImage, TestDriver, TrapChecker, WritebackChecker}

private[core] case class DataPrivilegeCase(name: String, mprv: Boolean = true,
  mpp: Int = 1, user: Boolean = false, readable: Boolean = true,
  mxr: Boolean = false, sum: Boolean = false, fault: Boolean = false)

// Use the complete Core/Bus/cache harness: a CSR-only test cannot detect a
// following load starting a physical transaction before MPRV takes effect.
private[core] class CorePrivilegeTester(c: CoreFetchFaultWrapper, scenario: DataPrivilegeCase)
    extends CoreTester(c) with AxiTestSupport {
  import InstEncoding._
  val memory = new InstructionImage
  val virtualAddress = BigInt(0x400000)
  val root = BigInt(0x10000)
  val leaf = BigInt(0x11000)
  val physicalAddress = BigInt(0x20000)
  val translated = scenario.mprv && scenario.mpp != 3
  val status = (if (scenario.mprv) 1 << 17 else 0) | (scenario.mpp << 11) |
    (if (scenario.mxr) 1 << 19 else 0) | (if (scenario.sum) 1 << 18 else 0)
  val program = Seq(
    addi(5, 0, 0x380), csrw(0x305, 5),
    lui(5, 0x80000), addi(5, 5, 0x10), csrw(0x180, 5),
    lui(8, 0x400),
    lui(5, (status + 0x800) >> 12), addi(5, 5, (status & 0xfff) - (if ((status & 0x800) != 0) 0x1000 else 0)),
    csrw(0x300, 5), lw(10, 8, 0), // Deliberately no gap after changing MPRV.
    addi(31, 0, 1), jal(0, 0))
  program.zipWithIndex.foreach { case (insn, i) => memory.place32(0x200 + i * 4, insn) }
  Seq(csrr(11, 0x342), csrr(12, 0x343), addi(31, 0, 1), jal(0, 0))
    .zipWithIndex.foreach { case (insn, i) => memory.place32(0x380 + i * 4, insn) }
  memory.place32(root + 4, ((leaf >> 12) << 10) | 1)
  memory.place32(leaf, ((physicalAddress >> 12) << 10) | 0xc9 |
    (if (scenario.readable) 2 else 0) | (if (scenario.user) 0x10 else 0))
  memory.place32(physicalAddress, 0x12345678)
  memory.place32(virtualAddress, 0x87654321L)
  val loadPc = BigInt(0x224)
  val donePc = if (scenario.fault) BigInt(0x388) else BigInt(0x228)
  val traps = new TrapChecker(if (scenario.fault)
    Seq(ExpectedTrap(loadPc, 13, virtualAddress)) else Seq.empty)
  val writes = new WritebackChecker((if (scenario.fault) Seq(
    ExpectedWriteback(0x380, 11, 13), ExpectedWriteback(0x384, 12, virtualAddress)) else
    Seq(ExpectedWriteback(loadPc, 10, if (translated) BigInt(0x12345678) else BigInt(0x87654321L)))) :+
    ExpectedWriteback(donePc, 31, 1))
  val ports = Seq(c.io.inst, c.io.data, c.io.uncached)
  ports.foreach(idleAxi)
  val responders = ports.map(port => new AxiReadResponder(port, memory.read32))
  var cycle = 0
  runUntil(2500, s"MPRV ${scenario.name}; ${writes.missing}; ${traps.missing}") {
    responders.foreach(_.beforeStep(allowAddress = cycle % 3 != 0, allowData = cycle % 5 != 1))
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val wb = sampleDebug(c.io.observation.debug)
      writes.observe(wb)
      done = wb.pc == donePc
    }
    step(1)
    responders.foreach(_.afterStep())
    cycle += 1
    done
  }
  writes.checkComplete()
  traps.checkComplete()
  if (translated) {
    assert(!responders(1).requests.exists(_.address == virtualAddress),
      "Load issued at its untranslated address while the older MSTATUS write was committing")
  }
}

class CorePrivilegeTest extends AnyFunSuite {
  test("enforce effective privilege and page permissions") {
    val cases = Seq(
      DataPrivilegeCase("supervisor"),
      DataPrivilegeCase("user-mxr", mpp = 0, user = true, readable = false, mxr = true),
      DataPrivilegeCase("supervisor-sum-mxr", user = true, readable = false, mxr = true, sum = true),
      DataPrivilegeCase("sum-denied", user = true, fault = true),
      DataPrivilegeCase("mxr-denied", readable = false, fault = true),
      DataPrivilegeCase("mprv-off", mprv = false),
      DataPrivilegeCase("machine-effective", mpp = 3))
    for (scenario <- cases) {
      TestDriver.run(new CoreFetchFaultWrapper) { c =>
        new CorePrivilegeTester(c, scenario)
      }
    }
  }
}
