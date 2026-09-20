package lsu

import chisel3._
import io._
import consts.CSR.CSR_MODE_S
import consts.LsuOp._
import consts.ExceptType._
import utils.{PeekPokeTester, TestDriver}

class AtomicPermissionHarness extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(LSU_OP_WIDTH.W))
    val flush = Input(Bool())
    val stall = Output(Bool())
    val trap = Output(Bool())
    val cause = Output(UInt(32.W))
    val result = Output(UInt(32.W))
    val pageTable = new _root_.axi.AxiMaster(32, 32)
    val demand = new _root_.axi.AxiMaster(32, 32)
  })
  val mem = Module(new core.Mem)
  val bus = Module(new _root_.bus.CoreBus)
  mem.io.alu := 0.U.asTypeOf(new AluIO)
  mem.io.alu.valid := true.B
  mem.io.alu.lsuOp := io.op
  mem.io.alu.excType := Mux(io.op === LSU_LR, EXC_LOAD,
    Mux(io.op === LSU_NOP, EXC_NONE, EXC_STAMO))
  mem.io.alu.reg.data := 0x40000000.U
  mem.io.alu.lsuData := 3.U
  mem.io.flush := io.flush
  mem.io.csrHasInt := false.B
  mem.io.csrBusy := false.B
  mem.io.csrMode := CSR_MODE_S
  mem.io.excMon.valid := false.B // exercise failed SC
  mem.io.ram <> bus.io.ram
  bus.io.rom.en := false.B
  bus.io.rom.wen := 0.U
  bus.io.rom.addr := 0.U
  bus.io.rom.wdata := 0.U
  bus.io.tlb.en := true.B
  bus.io.tlb.dataWrite := mem.io.writeIntent
  bus.io.tlb.flushInst := false.B
  bus.io.tlb.flushData := false.B
  bus.io.tlb.basePpn := 0x80000.U
  bus.io.tlb.sum := false.B
  bus.io.tlb.smode := true.B
  bus.io.cache.flushInst := mem.io.flushIc
  bus.io.cache.flushData := mem.io.flushDc
  mem.io.flushDcDone := bus.io.cache.flushDataDone
  mem.io.flushDcAccessFault := bus.io.cache.flushDataAccessFault
  bus.io.inst.readAddr.ready := false.B
  bus.io.inst.readData.valid := false.B
  bus.io.inst.readData.bits := 0.U.asTypeOf(bus.io.inst.readData.bits)
  bus.io.inst.writeAddr.ready := false.B
  bus.io.inst.writeData.ready := false.B
  bus.io.inst.writeResp.valid := false.B
  bus.io.inst.writeResp.bits := 0.U.asTypeOf(bus.io.inst.writeResp.bits)
  io.pageTable <> bus.io.data
  io.demand <> bus.io.uncached
  io.stall := mem.io.stallReq
  io.trap := mem.io.except.hasTrap
  io.cause := mem.io.except.excCause
  io.result := mem.io.mem.reg.data
}

class AtomicPermissionTester(c: AtomicPermissionHarness, writable: Boolean, dirty: Boolean)
    extends PeekPokeTester(c) {
  for (port <- Seq(c.io.pageTable, c.io.demand)) {
    poke(port.readAddr.ready, false)
    poke(port.readData.valid, false)
    poke(port.readData.bits.id, 0)
    poke(port.readData.bits.data, 0)
    poke(port.readData.bits.last, false)
    poke(port.readData.bits.resp, 0)
    poke(port.writeAddr.ready, false)
    poke(port.writeData.ready, false)
    poke(port.writeResp.valid, false)
    poke(port.writeResp.bits.id, 0)
    poke(port.writeResp.bits.resp, 0)
  }
  poke(c.io.op, LSU_LR)
  poke(c.io.flush, false)

  def waitFor(condition: => Boolean): Unit = {
    var elapsed = 0
    while (!condition && elapsed < 100) { step(1); elapsed += 1 }
    assert(condition, "atomic permission check timed out")
  }
  def read(port: _root_.axi.AxiMaster, value: BigInt, address: BigInt): Unit = {
    waitFor(peek(port.readAddr.valid) != 0)
    expect(port.readAddr.bits.addr, address)
    val beats = peek(port.readAddr.bits.len).toInt + 1
    step(2)
    poke(port.readAddr.ready, true)
    step(1)
    poke(port.readAddr.ready, false)
    for (i <- 0 until beats) {
      step(2)
      poke(port.readData.bits.data, value)
      poke(port.readData.bits.last, i == beats - 1)
      poke(port.readData.valid, true)
      waitFor(peek(port.readData.ready) != 0)
      step(1)
      poke(port.readData.valid, false)
    }
  }
  def idle(): Unit = {
    poke(c.io.op, LSU_NOP)
    for (_ <- 0 until 4) {
      expect(c.io.demand.readAddr.valid, false)
      expect(c.io.demand.writeAddr.valid, false)
      step(1)
    }
  }

  // A supervisor superpage maps to uncached physical memory. LR only needs
  // R and A, so it primes the TLB even when W or D is absent.
  val pte = (BigInt(0x10000) << 10) | 0x43 |
    (if (writable) 4 else 0) | (if (dirty) 0x80 else 0)
  read(c.io.pageTable, pte, BigInt("80000400", 16))
  read(c.io.demand, 5, BigInt("10000000", 16))
  waitFor(peek(c.io.stall) == 0)
  expect(c.io.trap, false)
  step(1)
  idle()

  for (op <- Seq(LSU_SC, LSU_ADD)) {
    poke(c.io.op, op)
    if (!writable || !dirty) {
      waitFor(peek(c.io.trap) != 0 || peek(c.io.demand.readAddr.valid) != 0)
      expect(c.io.trap, true)
      expect(c.io.cause, 15) // store/AMO page fault, even for a failed SC
      expect(c.io.demand.readAddr.valid, false)
      expect(c.io.demand.writeAddr.valid, false)
      poke(c.io.flush, true)
      step(1)
      poke(c.io.flush, false)
      idle()
    } else {
      // Keep the existing failed-SC transport behavior: it performs a read
      // with no write strobe, returns failure, and cannot modify memory.
      read(c.io.demand, 5, BigInt("10000000", 16))
      if (op.litValue == LSU_ADD.litValue) {
        waitFor(peek(c.io.demand.writeAddr.valid) != 0)
        expect(c.io.demand.writeAddr.bits.addr, BigInt("10000000", 16))
        poke(c.io.demand.writeAddr.ready, true)
        step(1)
        poke(c.io.demand.writeAddr.ready, false)
        waitFor(peek(c.io.demand.writeData.valid) != 0)
        expect(c.io.demand.writeData.bits.data, 8)
        expect(c.io.demand.writeData.bits.strb, 15)
        poke(c.io.demand.writeData.ready, true)
        step(1)
        poke(c.io.demand.writeData.ready, false)
        poke(c.io.demand.writeResp.valid, true)
        waitFor(peek(c.io.demand.writeResp.ready) != 0)
        step(1)
        poke(c.io.demand.writeResp.valid, false)
      }
      waitFor(peek(c.io.stall) == 0)
      expect(c.io.trap, false)
      expect(c.io.result, if (op.litValue == LSU_SC.litValue) 1 else 5)
      step(1)
      idle()
    }
  }
}

object AtomicPermissionTest extends App {
  for ((writable, dirty) <- Seq((false, true), (true, false), (true, true))) {
    if (!TestDriver.execute(args, () => new AtomicPermissionHarness) {
      c => new AtomicPermissionTester(c, writable, dirty)
    }) sys.exit(1)
  }
}
