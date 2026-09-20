package lsu

import chisel3._
import chisel3.util._
import core._
import io._
import consts.Parameters._
import consts.CSR.CSR_MODE_M
import utils.{PeekPokeTester, TestDriver}

class AtomicFaultHarness extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(32.W))
    val address = Input(UInt(32.W))
    val pageFault = Input(Bool())
    val accessFault = Input(Bool())
    val hasTrap = Output(Bool())
    val cause = Output(UInt(32.W))
    val value = Output(UInt(32.W))
    val request = Output(Bool())
  })
  val decoder = Module(new Decoder)
  val alu = Module(new ALU)
  val mem = Module(new core.Mem)
  decoder.io.fetch := 0.U.asTypeOf(new FetchIO)
  decoder.io.fetch.valid := true.B
  decoder.io.fetch.pc := 0x200.U
  decoder.io.inst := io.instruction
  decoder.io.stallId := false.B
  decoder.io.read1.data := io.address
  decoder.io.read2.data := 0.U
  alu.io.decoder <> decoder.io.decoder
  alu.io.flush := false.B
  alu.io.csrRead.valid := true.B
  alu.io.csrRead.data := 0.U
  mem.io.alu <> alu.io.alu
  mem.io.flush := false.B
  mem.io.ram.rdata := 0.U
  mem.io.ram.valid := true.B
  mem.io.ram.fault := io.pageFault
  mem.io.ram.accessFault := io.accessFault
  mem.io.csrHasInt := false.B
  mem.io.csrBusy := false.B
  mem.io.csrMode := CSR_MODE_M
  mem.io.flushDcDone := true.B
  mem.io.flushDcAccessFault := false.B
  mem.io.excMon.valid := false.B
  io.hasTrap := mem.io.except.hasTrap
  io.cause := mem.io.except.excCause
  io.value := mem.io.except.excValue
  io.request := mem.io.ram.en
}

object AtomicFaultTest extends App {
  if (!TestDriver.execute(args, () => new AtomicFaultHarness) { c => new PeekPokeTester(c) {
    // Decode real LR/SC/AMO encodings through the ALU and memory stage.
    for ((instruction, alignment, page, access) <- Seq(
      (0x1000a22f, 4, 13, 5), // lr.w x4, (x1)
      (0x1830a22f, 6, 15, 7), // sc.w x4, x3, (x1)
      (0x0030a22f, 6, 15, 7)  // amoadd.w x4, x3, (x1)
    )) {
      poke(c.io.instruction, instruction)
      poke(c.io.address, 0x101)
      poke(c.io.pageFault, false)
      poke(c.io.accessFault, false)
      expect(c.io.hasTrap, true)
      expect(c.io.cause, alignment)
      expect(c.io.value, 0x101)
      expect(c.io.request, false)

      poke(c.io.address, 0x100)
      poke(c.io.pageFault, true)
      expect(c.io.hasTrap, true)
      expect(c.io.cause, page)
      expect(c.io.value, 0x100)
      expect(c.io.request, false)

      poke(c.io.pageFault, false)
      poke(c.io.accessFault, true)
      expect(c.io.hasTrap, true)
      expect(c.io.cause, access)
      expect(c.io.value, 0x100)
      expect(c.io.request, false)
    }
  }}) sys.exit(1)
}
