package core

import chisel3._
import axi.AxiMaster
import bus.CoreBus
import consts.Parameters._
import utils.{AxiTestSupport, InstructionImage, TestDriver}

private[core] case class FetchPageCase(name: String, secondPagePresent: Boolean,
                                       accessFault: Boolean = false, compressedJump: Boolean = false)

private[core] class CoreFetchFaultWrapper extends Module {
  val io = IO(new Bundle {
    val observation = new CoreObservation
    val inst = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val data = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val uncached = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })
  val core = Module(new Core(FETCH_QUEUE_DEPTH))
  val bus = Module(new CoreBus)
  core.io.irq.timer := false.B
  core.io.irq.soft := false.B
  core.io.irq.extern := false.B
  core.io.tlb <> bus.io.tlb
  core.io.cache <> bus.io.cache
  core.io.rom <> bus.io.rom
  core.io.ram <> bus.io.ram
  bus.io.inst <> io.inst
  bus.io.data <> io.data
  bus.io.uncached <> io.uncached
  CoreObservation.connect(io.observation, core)
}

private[core] class CoreFetchFaultTester(c: CoreFetchFaultWrapper, scenario: FetchPageCase)
    extends CoreTester(c) with AxiTestSupport {
  import InstEncoding._
  val memory = new InstructionImage
  val root = BigInt(0x10000)
  val leaf = BigInt(0x11000)
  val firstPage = BigInt(0x20000)
  val secondPage = BigInt(0x40000)

  def put(address: BigInt, instruction: Long): Unit = memory.place32(address, instruction)
  val boot = Seq(
    addi(5, 0, 0x380),
    csrw(0x305, 5), // Machine trap handler uses Bare addressing.
    lui(5, 0x80000),
    addi(5, 5, 0x10),
    csrw(0x180, 5), // SATP = Sv32, root PPN = 0x10.
    lui(5, 1),
    addi(5, 5, -2),
    csrw(0x341, 5), // MEPC = 0xffe, preserving bit 1.
    addi(8, 0, 0x202),
    addi(5, 0, 1),
    slli(5, 5, 11),
    csrw(0x300, 5), // MPP = S, interrupts disabled.
    mret(),
  )
  boot.zipWithIndex.foreach { case (instruction, index) => put(0x200 + index * 4, instruction) }
  Seq(csrr(6, 0x342), csrr(7, 0x341), csrr(28, 0x343),
    addi(31, 0, 1), jal(0, 0)).zipWithIndex.foreach { case (instruction, index) =>
    put(0x380 + index * 4, instruction)
  }

  memory.place32(root, ((leaf >> 12) << 10) | 1)
  memory.place32(leaf, ((firstPage >> 12) << 10) | 0x4b) // V,R,X,A; supervisor executable.
  memory.place32(leaf + 4, if (scenario.secondPagePresent) ((secondPage >> 12) << 10) | 0x4b else 0)
  if (scenario.compressedJump) {
    // The next virtual page is unmapped, but this final 16-bit instruction
    // jumps back into the first page and must never consume that page fault.
    memory.place16(firstPage + 0xffc, c_nop())
    memory.place16(firstPage + 0xffe, c_jr(8)) // VA 0xffe
    memory.place16(firstPage + 0x200, c_nop())
    memory.place16(firstPage + 0x202, c_li(31, 1)) // VA 0x202
    memory.place16(firstPage + 0x204, c_j(0))
    memory.place16(firstPage + 0x206, c_nop())
  } else {
    val splitInstruction = addi(10, 0, 42)
    memory.place16(firstPage + 0xffc, c_nop())
    memory.place16(firstPage + 0xffe, splitInstruction & 0xffff)
    memory.place16(secondPage, splitInstruction >>> 16)
    memory.place16(secondPage + 2, c_nop()) // c.nop at VA 0x1002
    put(secondPage + 4, addi(31, 0, 1))
    memory.place16(secondPage + 8, c_j(0))
    memory.place16(secondPage + 10, c_nop())
  }

  for (port <- Seq(c.io.inst, c.io.data, c.io.uncached)) {
    idleAxi(port)
  }

  val willTrap = !scenario.compressedJump && (!scenario.secondPagePresent || scenario.accessFault)
  val expectedCause = if (scenario.accessFault) 1 else 12
  val donePc = if (willTrap) 0x38c else if (scenario.compressedJump) 0x202 else 0x1004
  val retired = scala.collection.mutable.ArrayBuffer.empty[BigInt]
  val traps = new TrapChecker(if (willTrap) Seq(ExpectedTrap(0xffe, expectedCause, 0x1000)) else Seq.empty)
  val writebacks = new WritebackChecker((if (willTrap) Seq(
    ExpectedWriteback(0x380, 6, expectedCause),
    ExpectedWriteback(0x384, 7, 0xffe),
    ExpectedWriteback(0x388, 28, 0x1000))
    else if (!scenario.compressedJump) Seq(ExpectedWriteback(0xffe, 10, 42))
    else Seq.empty) :+ ExpectedWriteback(donePc, 31, 1))
  val checkers: Seq[Checker] = Seq(writebacks, traps)
  var cycle = 0

  def physicalRegionMapped(base: BigInt): Boolean =
    (base >= 0x200 && base < 0x400) ||
      (base >= root && base < leaf + 0x1000) ||
      (base >= firstPage && base < firstPage + 0x1000) ||
      (base >= secondPage && base < secondPage + 0x1000)

  val responder = new AxiReadResponder(c.io.inst, memory.read32,
    (request, beat) => if (!physicalRegionMapped(request.address) ||
      (scenario.accessFault && request.address == secondPage && beat == 3)) 2 else 0)

  runUntil(3000, s"Sv32 case ${scenario.name}; missing events: ${checkers.map(_.missing).mkString("; ")}") {
    // Exercise independently stalled AXI address and response channels.
    responder.beforeStep(allowAddress = cycle % 3 != 0, allowData = cycle % 5 != 1).foreach { request =>
      assert(request.beats == 16, "Expected the real 64-byte I-cache refill")
    }
    for (port <- Seq(c.io.data, c.io.uncached)) {
      expect(port.readAddr.valid, false)
      expect(port.writeAddr.valid, false)
    }
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      retired += actual.pc
      assert(!willTrap || actual.pc != 0xffe, "Faulting split instruction retired")
      writebacks.observe(actual)
      done = actual.pc == donePc
    }
    step(1)
    responder.afterStep()
    cycle += 1
    done
  }

  val reads = responder.requests.map(_.address)
  assert(reads.contains(firstPage + 0xfc0), "Did not fetch the end of the first physical page")
  assert(!reads.contains(BigInt(0xfc0)), "MRET fetched its target before restoring Sv32 translation")
  assert(!reads.contains(firstPage + 0x1000), "Incorrectly continued at the adjacent physical page")
  if (scenario.secondPagePresent) assert(reads.contains(secondPage), "Skipped the second page translation")
  else assert(!reads.contains(secondPage), "Fetched an unmapped second page")
  if (willTrap) {
    assert(!retired.contains(BigInt(0xffe)))
  } else {
    assert(retired.count(_ == BigInt(0xffe)) == 1)
    if (!scenario.compressedJump) {
      assert(retired.contains(BigInt(0x1002)))
    }
  }
  checkers.foreach(_.checkComplete())
  println(s"CoreFetchFault: ${scenario.name} passed after $cycle cycles.")
}

object CoreFetchFaultTest extends App {
  for (scenario <- Seq(
    FetchPageCase("split instruction across noncontiguous physical pages", secondPagePresent = true),
    FetchPageCase("second instruction parcel page fault", secondPagePresent = false),
    FetchPageCase("second instruction parcel AXI access fault", secondPagePresent = true, accessFault = true),
    FetchPageCase("final compressed jump before unmapped page", secondPagePresent = false, compressedJump = true),
  )) {
    if (!TestDriver.execute(args, () => new CoreFetchFaultWrapper) {
      c => new CoreFetchFaultTester(c, scenario)
    }) sys.exit(1)
  }
}
