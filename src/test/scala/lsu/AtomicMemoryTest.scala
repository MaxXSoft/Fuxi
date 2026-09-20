package lsu

import chisel3._
import io._
import consts.CSR.CSR_MODE_M
import consts.LsuOp._
import consts.ExceptType._
import consts.Parameters.DCACHE_LINE_SIZE
import utils.{PeekPokeTester, TestDriver}

// Exercise the MEM completion boundary with the actual cached and uncached
// buses. The AXI model delays every channel independently.
class AtomicMemoryHarness(cached: Boolean) extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(consts.LsuOp.LSU_OP_WIDTH.W))
    val address = Input(UInt(32.W))
    val operand = Input(UInt(32.W))
    val busy = Input(Bool())
    val irq = Input(Bool())
    val flush = Input(Bool())
    val stall = Output(Bool())
    val result = Output(UInt(32.W))
    val readData = Output(UInt(32.W))
    val request = Output(Bool())
    val trap = Output(Bool())
    val interrupt = Output(Bool())
    val cause = Output(UInt(32.W))
    val axi = new _root_.axi.AxiMaster(32, 32)
  })
  val mem = Module(new core.Mem)
  mem.io.alu := 0.U.asTypeOf(new AluIO)
  mem.io.alu.valid := true.B
  mem.io.alu.lsuOp := io.op
  mem.io.alu.excType := Mux(io.op === LSU_NOP || io.op === LSU_FENI,
    EXC_NONE, Mux(io.op === LSU_LW, EXC_LOAD, EXC_STAMO))
  mem.io.alu.reg.data := io.address
  mem.io.alu.lsuData := io.operand
  mem.io.flush := io.flush
  mem.io.csrHasInt := io.irq
  mem.io.csrBusy := io.busy
  mem.io.csrMode := CSR_MODE_M
  mem.io.excMon.valid := false.B
  if (cached) {
    val cache = Module(new bus.DataCache)
    mem.io.ram <> cache.io.sram
    cache.io.flush := mem.io.flushDc
    mem.io.flushDcDone := cache.io.flushDone
    mem.io.flushDcAccessFault := cache.io.flushAccessFault
    io.axi <> cache.io.axi
  } else {
    val uncached = Module(new bus.Uncached)
    mem.io.ram <> uncached.io.sram
    mem.io.flushDcDone := true.B
    mem.io.flushDcAccessFault := false.B
    io.axi <> uncached.io.axi
  }
  io.stall := mem.io.stallReq
  io.result := mem.io.mem.reg.data
  io.readData := mem.io.ram.rdata
  io.request := mem.io.ram.en
  io.trap := mem.io.except.hasTrap
  io.interrupt := mem.io.except.isInterrupt
  io.cause := mem.io.except.excCause
}

class AtomicMemoryTester(c: AtomicMemoryHarness, cached: Boolean)
    extends PeekPokeTester(c) {
  val base = BigInt("10000000", 16)
  val memory = scala.collection.mutable.Map[BigInt, BigInt](base -> 5, (base + 4) -> 21)
  var read: Option[(BigInt, Int, Int, Int)] = None // address, beats, index, response
  var write: Option[(BigInt, Int)] = None
  var response: Option[Int] = None
  var readError = false
  var writeError = false
  var reads = 0
  var writes = 0
  var ticks = 0
  poke(c.io.op, LSU_NOP)
  poke(c.io.address, base)
  poke(c.io.operand, 0)
  poke(c.io.busy, false)
  poke(c.io.irq, false)
  poke(c.io.flush, false)

  def cycle(): Unit = {
    val arReady = ticks % 3 == 1 && read.isEmpty
    val awReady = ticks % 4 == 2 && write.isEmpty && response.isEmpty
    val wReady = ticks % 3 == 2 && write.nonEmpty
    val rValid = ticks % 3 == 0 && read.nonEmpty
    val bValid = ticks % 4 == 1 && response.nonEmpty
    poke(c.io.axi.readAddr.ready, arReady)
    poke(c.io.axi.writeAddr.ready, awReady)
    poke(c.io.axi.writeData.ready, wReady)
    poke(c.io.axi.readData.valid, rValid)
    poke(c.io.axi.readData.bits.id, 0)
    poke(c.io.axi.readData.bits.data, read.map { case (addr, _, index, _) =>
      memory.getOrElse(addr + index * 4, BigInt(0)) }.getOrElse(BigInt(0)))
    poke(c.io.axi.readData.bits.last, read.exists { case (_, beats, index, _) => index == beats - 1 })
    poke(c.io.axi.readData.bits.resp, read.map(_._4).getOrElse(0))
    poke(c.io.axi.writeResp.valid, bValid)
    poke(c.io.axi.writeResp.bits.id, 0)
    poke(c.io.axi.writeResp.bits.resp, response.getOrElse(0))
    val takeAr = arReady && peek(c.io.axi.readAddr.valid) != 0
    val takeAw = awReady && peek(c.io.axi.writeAddr.valid) != 0
    val takeW = wReady && peek(c.io.axi.writeData.valid) != 0
    val takeR = rValid && peek(c.io.axi.readData.ready) != 0
    val takeB = bValid && peek(c.io.axi.writeResp.ready) != 0
    val arAddr = peek(c.io.axi.readAddr.bits.addr)
    val arBeats = peek(c.io.axi.readAddr.bits.len).toInt + 1
    val awAddr = peek(c.io.axi.writeAddr.bits.addr)
    val wData = peek(c.io.axi.writeData.bits.data)
    val wLast = peek(c.io.axi.writeData.bits.last) != 0
    if (takeW) expect(c.io.axi.writeData.bits.strb, 15)
    step(1)
    if (takeR) read = read.flatMap { case (addr, beats, index, resp) =>
      if (index == beats - 1) None else Some((addr, beats, index + 1, resp)) }
    if (takeB) response = None
    if (takeAr) {
      reads += 1
      read = Some((arAddr, arBeats, 0, if (readError) 2 else 0))
      readError = false
    }
    if (takeAw) { writes += 1; write = Some((awAddr, 0)) }
    if (takeW) {
      val (addr, index) = write.get
      if (!writeError) memory(addr + index * 4) = wData
      if (wLast) {
        write = None
        response = Some(if (writeError) 2 else 0)
        writeError = false
      } else write = Some((addr, index + 1))
    }
    ticks += 1
  }

  def waitFor(condition: => Boolean): Unit = {
    var elapsed = 0
    while (!condition && elapsed < 2000) { cycle(); elapsed += 1 }
    assert(condition, "atomic memory transaction timed out")
  }
  def idle(): Unit = {
    poke(c.io.op, LSU_NOP)
    for (_ <- 0 until 12) {
      expect(c.io.request, false)
      expect(c.io.axi.readAddr.valid, false)
      expect(c.io.axi.writeAddr.valid, false)
      cycle()
    }
  }
  def amo(address: BigInt, operand: Int, old: Int, hold: Boolean = false): Unit = {
    poke(c.io.op, LSU_ADD)
    poke(c.io.address, address)
    poke(c.io.operand, operand)
    expect(c.io.stall, true)
    waitFor(peek(c.io.stall) == 0)
    expect(c.io.trap, false)
    expect(c.io.request, false)
    expect(c.io.result, old)
    if (hold) {
      poke(c.io.busy, true)
      for (_ <- 0 until 7) {
        expect(c.io.stall, true)
        expect(c.io.request, false)
        expect(c.io.result, old)
        cycle()
      }
      poke(c.io.busy, false)
    }
    cycle() // consume; next operation may immediately replace this one
  }
  def flushCache(): Unit = {
    poke(c.io.op, LSU_FENI)
    waitFor(peek(c.io.stall) == 0)
    cycle()
    idle()
  }

  idle()
  amo(base, 3, 5)
  // No idle cycle between adjacent AMOs, including a different word in a line.
  amo(base + 4, 7, 21, hold = true)
  amo(base, 4, 8)
  idle()
  if (cached) {
    expect(c.io.axi.readAddr.valid, false)
    assert(reads == 1, s"cached AMOs unexpectedly refilled $reads times")
    assert(writes == 0, "write-back cache unexpectedly emitted an AXI store")
    flushCache()
  } else {
    assert(reads == 3 && writes == 3, s"AMOs issued $reads reads and $writes writes")
  }
  assert(memory(base) == 12 && memory(base + 4) == 28, "incorrect atomic memory updates")

  // A synchronous read error must abort before the store phase. Flush also
  // resets AMO state, allowing the following instruction to start normally.
  val faultAddress = base + DCACHE_LINE_SIZE * 2
  val writesBeforeFault = writes
  readError = true
  poke(c.io.op, LSU_ADD)
  poke(c.io.address, faultAddress)
  poke(c.io.operand, 9)
  waitFor(peek(c.io.trap) != 0)
  expect(c.io.cause, 7)
  expect(c.io.interrupt, false)
  expect(c.io.request, false)
  poke(c.io.flush, true)
  cycle()
  poke(c.io.flush, false)
  idle()
  assert(writes == writesBeforeFault, "faulting AMO issued a store")
  if (!cached) {
    writeError = true
    poke(c.io.op, LSU_ADD)
    poke(c.io.address, base)
    poke(c.io.operand, 99)
    waitFor(peek(c.io.trap) != 0)
    expect(c.io.cause, 7)
    expect(c.io.request, false)
    poke(c.io.flush, true)
    cycle()
    poke(c.io.flush, false)
    idle()
    assert(memory(base) == 12, "failed AXI write changed memory")
  }
  amo(base, 1, 12)
  idle()

  // An IRQ before starting suppresses the operation, while an IRQ arriving
  // in flight remains pending until the completed AMO retires.
  poke(c.io.op, LSU_ADD)
  poke(c.io.address, base)
  poke(c.io.operand, 2)
  poke(c.io.irq, true)
  expect(c.io.trap, true)
  expect(c.io.interrupt, true)
  expect(c.io.request, false)
  poke(c.io.flush, true)
  cycle()
  poke(c.io.flush, false)
  poke(c.io.irq, false)
  cycle() // start the operation
  poke(c.io.irq, true)
  expect(c.io.trap, false)
  waitFor(peek(c.io.stall) == 0)
  expect(c.io.result, 13)
  expect(c.io.trap, false)
  cycle()
  poke(c.io.op, LSU_NOP)
  expect(c.io.trap, true)
  expect(c.io.interrupt, true)
  poke(c.io.flush, true)
  cycle()
  poke(c.io.flush, false)
  poke(c.io.irq, false)
  idle()
  if (cached) flushCache()
  assert(memory(base) == 15, "interrupted AMO did not complete exactly once")
}

object AtomicMemoryTest extends App {
  for (cached <- Seq(false, true)) {
    if (!TestDriver.execute(args, () => new AtomicMemoryHarness(cached)) {
      c => new AtomicMemoryTester(c, cached)
    }) sys.exit(1)
  }
}
