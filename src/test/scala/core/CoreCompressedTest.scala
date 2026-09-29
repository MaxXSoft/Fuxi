package core

import chisel3._
import chisel3.util.experimental.BoringUtils
import consts.Parameters._
import io.DebugIO
import sim.{RAM, ROM}
import utils.TestDriver

private[core] case class RvcRetirement(pc: BigInt, write: Option[(Int, BigInt)] = None)
private[core] case class RvcTrap(pc: BigInt, cause: BigInt, value: BigInt)

private[core] class RvcProgram {
  private val parcels = scala.collection.mutable.Map.empty[Int, Int]
  val retired = scala.collection.mutable.ArrayBuffer.empty[RvcRetirement]
  val traps = scala.collection.mutable.ArrayBuffer.empty[RvcTrap]
  var pc = RESET_PC.litValue.toInt
  var donePc = 0

  def place16(address: Int, instruction: Int): Unit = {
    require((address & 1) == 0 && instruction >= 0 && instruction < 65536)
    require(address >= RESET_PC.litValue.toInt && address < RESET_PC.litValue.toInt + ROM.DEPTH * 4)
    require(!parcels.contains(address), f"Overlapping instruction parcel at 0x$address%x")
    parcels(address) = instruction
  }
  def place32(address: Int, instruction: Long): Unit = {
    place16(address, (instruction & 0xffff).toInt)
    place16(address + 2, ((instruction >>> 16) & 0xffff).toInt)
  }
  def event(address: Int, write: Option[(Int, BigInt)] = None): Unit =
    retired += RvcRetirement(address, write)
  def half(instruction: Int, write: Option[(Int, BigInt)] = None, retires: Boolean = true): Int = {
    val address = pc
    place16(address, instruction)
    if (retires) event(address, write)
    pc += 2
    address
  }
  def word(instruction: Long, write: Option[(Int, BigInt)] = None, retires: Boolean = true): Int = {
    val address = pc
    place32(address, instruction)
    if (retires) event(address, write)
    pc += 4
    address
  }
  def finish(): Unit = {
    donePc = word(RiscvTestEncoding.addi(31, 0, 1), Some(31 -> BigInt(1)))
    half(0xa001, retires = false)
  }
  def words: Seq[BigInt] = {
    val base = RESET_PC.litValue.toInt
    (base to (parcels.keys.max | 3) by 4).map { address =>
      BigInt(parcels.getOrElse(address, 1)) |
        (BigInt(parcels.getOrElse(address + 2, 1)) << 16)
    }
  }
}

private[core] object CompressedFlowProgram extends RvcProgram {
  import RiscvTestEncoding._
  def write(rd: Int, data: Int): Option[(Int, BigInt)] = Some(rd -> BigInt(data))

  half(0x0001) // C.NOP makes the following full-width instruction straddle words.
  word(addi(2, 0, 128), write(2, 128))
  half(ci(2, 10, 5), write(10, 5))
  half(ci(2, 11, 7), write(11, 7))
  half(0x952e, write(10, 12)) // c.add a0, a1
  half(0xc02a) // c.swsp a0, 0(sp)
  half(lwsp(12, 0), write(12, 12))
  half(0x962a, write(12, 24)) // c.add a2, a0: load-use hazard

  private val firstReturn = pc + 2
  half(cj(1, 0x300 - pc), write(1, firstReturn))
  event(0x300, write(10, 13))
  event(0x302)

  word(addi(5, 0, 0x320), write(5, 0x320))
  word(sw(5, 2, 4))
  half(lwsp(5, 4), write(5, 0x320))
  private val secondReturn = pc + 2
  half(0x9282, write(1, secondReturn)) // c.jalr t0 immediately after the load
  event(0x320, write(13, secondReturn))
  event(0x322)

  word(addi(5, 0, 0x342), write(5, 0x342))
  word(sw(5, 2, 8))
  half(lwsp(5, 8), write(5, 0x342))
  half(0x8282) // c.jr t0 immediately after the load
  private val thirdReturn = pc
  event(0x342, write(12, 25))
  event(0x344)

  // HINTs must retire individually without changing their apparent operands.
  Seq(0x0001, 0x0005, 0x0501, 0x4005, 0x8006, 0x900a, 0x0006, 0x8001)
    .foreach(half(_))
  half(ci(2, 8, 0), write(8, 0))
  half(0xc019) // c.beqz s0, +6
  word(addi(20, 0, 99), retires = false)
  half(ci(0, 10, 1), write(10, 14))
  word(0x0000100f) // FENCE.I while younger packets accumulate in the FIFO.
  half(ci(0, 10, 1), write(10, 15))
  word(0x12000073) // SFENCE.VMA also restarts the younger stream.
  half(ci(0, 10, 1), write(10, 16))
  word(csr(0xc02, 0, 2, 14), write(14, retired.size))
  finish()

  place16(0x300, ci(0, 10, 1))
  place16(0x302, 0x8082)
  place16(0x320, 0x8686) // c.mv a3, ra
  place16(0x322, 0x8082)
  place16(0x342, ci(0, 12, 1))
  place32(0x344, jal(0, thirdReturn - 0x344))
}

private[core] object CompressedTrapProgram extends RvcProgram {
  import RiscvTestEncoding._
  def write(rd: Int, data: Int): Option[(Int, BigInt)] = Some(rd -> BigInt(data))
  word(addi(5, 0, 0x380), write(5, 0x380))
  word(csr(0x305, 5))
  for ((instruction, cause) <- Seq(0x0000 -> 2, 0x9c01 -> 2, 0x9002 -> 3, 0x0000 -> 2)) {
    val faultPc = half(instruction, retires = false)
    val value = if (cause == 2) instruction else 0
    traps += RvcTrap(faultPc, cause, value)
    event(0x380, write(6, cause))
    event(0x384, write(7, faultPc))
    event(0x388, write(28, value))
    event(0x38c, write(7, faultPc + 2))
    event(0x390)
    event(0x394)
  }
  // SRET must also preserve bit 1 of SEPC; run its target in supervisor mode.
  word(addi(5, 0, 0x362), write(5, 0x362))
  word(csr(0x141, 5))
  word(addi(5, 0, 0x100), write(5, 0x100))
  word(csr(0x100, 5))
  word(0x10200073)
  word(addi(20, 0, 99), retires = false)
  pc = 0x362
  half(ci(2, 10, 9), write(10, 9))
  finish()

  place32(0x380, csr(0x342, 0, 2, 6))
  place32(0x384, csr(0x341, 0, 2, 7))
  place32(0x388, csr(0x343, 0, 2, 28))
  place32(0x38c, addi(7, 7, 2))
  place32(0x390, csr(0x341, 7))
  place32(0x394, 0x30200073)
}

private[core] class CoreCompressedWrapper(program: RvcProgram, depth: Int) extends Module {
  val io = IO(new Bundle {
    val debug = new DebugIO
    val memoryStall = Input(Bool())
    val fenceStall = Input(Bool())
    val memoryRequest = Output(Bool())
    val fenceRequest = Output(Bool())
    val queueCount = Output(UInt(32.W))
    val retired = Output(Bool())
    val count = Output(UInt(64.W))
    val trap = Output(Bool())
    val trapPc = Output(UInt(32.W))
    val trapCause = Output(UInt(32.W))
    val trapValue = Output(UInt(32.W))
  })
  val core = Module(new Core(depth))
  val rom = Module(new ROM(program.words))
  val ram = Module(new RAM)
  core.io.irq.timer := false.B
  core.io.irq.soft := false.B
  core.io.irq.extern := false.B
  core.io.rom <> rom.io
  core.io.ram <> ram.io
  ram.io.en := core.io.ram.en && !io.memoryStall
  core.io.ram.valid := ram.io.valid && !io.memoryStall
  core.io.cache.flushDataDone := !io.fenceStall
  core.io.cache.flushDataAccessFault := false.B
  io.debug <> core.io.debug
  io.memoryRequest := core.io.ram.en
  io.fenceRequest := core.io.cache.flushData
  io.queueCount := BoringUtils.bore(core.ifid.io.count)
  io.retired := BoringUtils.bore(core.wb.io.csr.retired)
  io.count := BoringUtils.bore(core.csrfile.minstret.data)
  val except = BoringUtils.bore(core.mem.io.except)
  io.trap := except.hasTrap && !except.isMret && !except.isSret
  io.trapPc := except.excPc
  io.trapCause := except.excCause
  io.trapValue := except.excValue
}

private[core] class CoreCompressedTester(c: CoreCompressedWrapper, program: RvcProgram,
                                         depth: Int, stall: Boolean) extends CoreTester(c) {
  val expected = scala.collection.mutable.Queue.from(program.retired)
  val traps = scala.collection.mutable.Queue.from(program.traps)
  var retirementCount = 0
  var maxQueueCount = 0
  var memoryWait = 0
  var fenceWait = 0
  var memoryArmed = true
  var fenceArmed = true
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)

  runUntil(1800, s"missing retirements ${expected.mkString(", ")}") {
    if (peek(c.io.memoryRequest) == 0) memoryArmed = true
    else if (memoryArmed && stall) { memoryWait = 8; memoryArmed = false }
    if (peek(c.io.fenceRequest) == 0) fenceArmed = true
    else if (fenceArmed && stall) { fenceWait = 10; fenceArmed = false }
    poke(c.io.memoryStall, memoryWait > 0)
    poke(c.io.fenceStall, fenceWait > 0)
    memoryWait = math.max(0, memoryWait - 1)
    fenceWait = math.max(0, fenceWait - 1)
    maxQueueCount = math.max(maxQueueCount, peek(c.io.queueCount).toInt)

    expect(c.io.count, retirementCount)
    if (peek(c.io.trap) != 0) {
      assert(traps.nonEmpty, f"Unexpected trap at 0x${peek(c.io.trapPc)}%x")
      val trap = traps.dequeue()
      expect(c.io.trapPc, trap.pc)
      expect(c.io.trapCause, trap.cause)
      expect(c.io.trapValue, trap.value)
    }
    var done = false
    if (peek(c.io.retired) != 0) {
      assert(expected.nonEmpty, "Unexpected retirement after end of program")
      val instruction = expected.dequeue()
      expect(c.io.debug.pc, instruction.pc)
      instruction.write.foreach { case (rd, data) =>
        expect(c.io.debug.regWen, true)
        expect(c.io.debug.regWaddr, rd)
        expect(c.io.debug.regWdata, data)
      }
      retirementCount += 1
      done = instruction.pc == program.donePc
    }
    step(1)
    done
  }
  assert(expected.isEmpty && traps.isEmpty)
  expect(c.io.count, program.retired.size)
  if (stall) assert(maxQueueCount == depth, s"FIFO did not fill under forced stalls: $maxQueueCount/$depth")
}

object CoreCompressedTest extends App {
  for ((program, depth, stall) <- Seq(
    (CompressedFlowProgram, 1, true),
    (CompressedFlowProgram, 4, true),
    (CompressedTrapProgram, 4, false),
  )) {
    if (!TestDriver.execute(args, () => new CoreCompressedWrapper(program, depth)) {
      c => new CoreCompressedTester(c, program, depth, stall)
    }) sys.exit(1)
  }
}
