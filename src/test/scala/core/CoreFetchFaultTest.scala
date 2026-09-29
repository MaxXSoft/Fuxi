package core

import chisel3._
import chisel3.util.experimental.BoringUtils
import axi.AxiMaster
import bus.CoreBus
import consts.Parameters._
import io.DebugIO
import utils.TestDriver

private[core] case class FetchPageCase(name: String, secondPagePresent: Boolean,
                                       accessFault: Boolean = false, compressedJump: Boolean = false)

private[core] class CoreFetchFaultWrapper extends Module {
  val io = IO(new Bundle {
    val debug = new DebugIO
    val inst = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val data = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val uncached = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val retired = Output(Bool())
    val trap = Output(Bool())
    val trapPc = Output(UInt(32.W))
    val trapCause = Output(UInt(32.W))
    val trapValue = Output(UInt(32.W))
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
  io.debug <> core.io.debug
  io.retired := BoringUtils.bore(core.wb.io.csr.retired)
  val except = BoringUtils.bore(core.mem.io.except)
  io.trap := except.hasTrap && !except.isMret && !except.isSret
  io.trapPc := except.excPc
  io.trapCause := except.excCause
  io.trapValue := except.excValue
}

private[core] class CoreFetchFaultTester(c: CoreFetchFaultWrapper, scenario: FetchPageCase)
    extends CoreTester(c) {
  import RiscvTestEncoding._
  val memory = scala.collection.mutable.Map.empty[BigInt, BigInt]
  val root = BigInt(0x10000)
  val leaf = BigInt(0x11000)
  val firstPage = BigInt(0x20000)
  val secondPage = BigInt(0x40000)

  def put(address: BigInt, instruction: Long): Unit = memory(address) = BigInt(instruction)
  val boot = Seq(
    addi(5, 0, 0x380),
    csr(0x305, 5), // Machine trap handler uses Bare addressing.
    0x800002b7L,
    addi(5, 5, 0x10),
    csr(0x180, 5), // SATP = Sv32, root PPN = 0x10.
    0x000012b7L,
    addi(5, 5, -2),
    csr(0x341, 5), // MEPC = 0xffe, preserving bit 1.
    addi(8, 0, 0x202),
    addi(5, 0, 1),
    0x00b29293L,
    csr(0x300, 5), // MPP = S, interrupts disabled.
    0x30200073L,
  )
  boot.zipWithIndex.foreach { case (instruction, index) => put(0x200 + index * 4, instruction) }
  Seq(csr(0x342, 0, 2, 6), csr(0x341, 0, 2, 7), csr(0x343, 0, 2, 28),
    addi(31, 0, 1), jal(0, 0)).zipWithIndex.foreach { case (instruction, index) =>
    put(0x380 + index * 4, instruction)
  }

  memory(root) = ((leaf >> 12) << 10) | 1
  memory(leaf) = ((firstPage >> 12) << 10) | 0x4b // V,R,X,A; supervisor executable.
  memory(leaf + 4) = if (scenario.secondPagePresent) ((secondPage >> 12) << 10) | 0x4b else 0
  if (scenario.compressedJump) {
    // The next virtual page is unmapped, but this final 16-bit instruction
    // jumps back into the first page and must never consume that page fault.
    put(firstPage + 0xffc, 0x84020001L) // c.jr s0 at VA 0xffe
    put(firstPage + 0x200, 0x4f850001L) // c.li t6, 1 at VA 0x202
    put(firstPage + 0x204, 0x0001a001L)
  } else {
    put(firstPage + 0xffc, 0x05130001L) // low half of addi a0, zero, 42 at VA 0xffe
    put(secondPage, 0x000102a0L) // high half at VA 0x1000, then c.nop at VA 0x1002
    put(secondPage + 4, addi(31, 0, 1))
    put(secondPage + 8, 0x0001a001L)
  }

  for (port <- Seq(c.io.inst, c.io.data, c.io.uncached)) {
    poke(port.readAddr.ready, false)
    poke(port.readData.valid, false)
    poke(port.readData.bits.id, 0)
    poke(port.readData.bits.data, 0)
    poke(port.readData.bits.resp, 0)
    poke(port.readData.bits.last, false)
    poke(port.writeAddr.ready, false)
    poke(port.writeData.ready, false)
    poke(port.writeResp.valid, false)
    poke(port.writeResp.bits.id, 0)
    poke(port.writeResp.bits.resp, 0)
  }

  val willTrap = !scenario.compressedJump && (!scenario.secondPagePresent || scenario.accessFault)
  val expectedCause = if (scenario.accessFault) 1 else 12
  val donePc = if (willTrap) 0x38c else if (scenario.compressedJump) 0x202 else 0x1004
  val retired = scala.collection.mutable.ArrayBuffer.empty[BigInt]
  val reads = scala.collection.mutable.ArrayBuffer.empty[BigInt]
  val checkedCsrs = scala.collection.mutable.Set.empty[Int]
  var trapCount = 0
  var cycle = 0
  var active = false
  var address = BigInt(0)
  var beat = 0
  var beats = 0
  var sawSplitWrite = false

  def physicalRegionMapped(base: BigInt): Boolean =
    (base >= 0x200 && base < 0x400) ||
      (base >= root && base < leaf + 0x1000) ||
      (base >= firstPage && base < firstPage + 0x1000) ||
      (base >= secondPage && base < secondPage + 0x1000)

  runUntil(3000, s"Sv32 instruction-fetch case ${scenario.name} did not finish") {
    // Exercise independently stalled AXI address and response channels.
    poke(c.io.inst.readAddr.ready, !active && cycle % 3 != 0)
    val returning = active && cycle % 5 != 1
    poke(c.io.inst.readData.valid, returning)
    poke(c.io.inst.readData.bits.data, memory.getOrElse(address + beat * 4, BigInt(0)))
    poke(c.io.inst.readData.bits.last, active && beat == beats - 1)
    poke(c.io.inst.readData.bits.resp,
      if (!physicalRegionMapped(address) ||
        (scenario.accessFault && address == secondPage && beat == 3)) 2 else 0)

    val acceptAddress = peek(c.io.inst.readAddr.valid) != 0 && peek(c.io.inst.readAddr.ready) != 0
    val acceptData = returning && peek(c.io.inst.readData.ready) != 0
    val newAddress = if (acceptAddress) peek(c.io.inst.readAddr.bits.addr) else BigInt(0)
    val newBeats = if (acceptAddress) peek(c.io.inst.readAddr.bits.len).toInt + 1 else 0
    if (acceptAddress) {
      expect(c.io.inst.readAddr.bits.size, 2)
      assert(newBeats == 16, "Expected the real 64-byte I-cache refill")
      reads += newAddress
    }
    for (port <- Seq(c.io.data, c.io.uncached)) {
      expect(port.readAddr.valid, false)
      expect(port.writeAddr.valid, false)
    }
    if (peek(c.io.trap) != 0) {
      assert(willTrap && trapCount == 0, s"Unexpected trap in ${scenario.name}")
      expect(c.io.trapPc, 0xffe)
      expect(c.io.trapCause, expectedCause)
      expect(c.io.trapValue, 0x1000)
      trapCount += 1
    }

    var done = false
    if (peek(c.io.retired) != 0) {
      val pc = peek(c.io.debug.pc)
      retired += pc
      if (!scenario.compressedJump && pc == 0xffe) {
        assert(!willTrap, "Faulting split instruction retired")
        expect(c.io.debug.regWen, true)
        expect(c.io.debug.regWaddr, 10)
        expect(c.io.debug.regWdata, 42)
        sawSplitWrite = true
      }
      if (willTrap) {
        val checkpoint = Map(0x380 -> (6, expectedCause), 0x384 -> (7, 0xffe), 0x388 -> (28, 0x1000))
        checkpoint.get(pc.toInt).foreach { case (rd, value) =>
          expect(c.io.debug.regWen, true)
          expect(c.io.debug.regWaddr, rd)
          expect(c.io.debug.regWdata, value)
          checkedCsrs += pc.toInt
        }
      }
      if (pc == donePc) {
        expect(c.io.debug.regWen, true)
        expect(c.io.debug.regWaddr, 31)
        expect(c.io.debug.regWdata, 1)
        done = true
      }
    }
    step(1)
    if (acceptData) {
      beat += 1
      if (beat == beats) active = false
    }
    if (acceptAddress) {
      active = true
      address = newAddress
      beats = newBeats
      beat = 0
    }
    cycle += 1
    done
  }

  assert(reads.contains(firstPage + 0xfc0), "Did not fetch the end of the first physical page")
  assert(!reads.contains(BigInt(0xfc0)), "MRET fetched its target before restoring Sv32 translation")
  assert(!reads.contains(firstPage + 0x1000), "Incorrectly continued at the adjacent physical page")
  if (scenario.secondPagePresent) assert(reads.contains(secondPage), "Skipped the second page translation")
  else assert(!reads.contains(secondPage), "Fetched an unmapped second page")
  if (willTrap) {
    assert(trapCount == 1 && checkedCsrs.size == 3)
    assert(!retired.contains(BigInt(0xffe)))
  } else {
    assert(trapCount == 0)
    assert(retired.count(_ == BigInt(0xffe)) == 1)
    if (!scenario.compressedJump) {
      assert(sawSplitWrite && retired.contains(BigInt(0x1002)))
    }
  }
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
