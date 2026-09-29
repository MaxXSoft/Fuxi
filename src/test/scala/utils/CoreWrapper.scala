package utils

import chisel3._
import core.Core
import chisel3.util.experimental.BoringUtils
import consts.Parameters.FETCH_QUEUE_DEPTH
import io.DebugIO
import sim.{RAM, ROM}

// The ROM-backed system can be driven cycle by cycle by specialized tests.
class CoreMemoryHarness(init: ROM.Init, depth: Int = FETCH_QUEUE_DEPTH,
                        fenceFault: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val observation = new CoreObservation
    val memoryStall = Input(Bool())
    val fenceStall = Input(Bool())
    val memoryRequest = Output(Bool())
    val fenceRequest = Output(Bool())
    val queueCount = Output(UInt(32.W))
  })
  val core = Module(new Core(depth))
  val rom = Module(new ROM(init))
  val ram = Module(new RAM)
  core.io.irq.timer := false.B
  core.io.irq.soft := false.B
  core.io.irq.extern := false.B
  core.io.rom <> rom.io
  core.io.ram <> ram.io
  ram.io.en := core.io.ram.en && !io.memoryStall
  core.io.ram.valid := ram.io.valid && !io.memoryStall
  core.io.cache.flushDataDone := !io.fenceStall
  core.io.cache.flushDataAccessFault := fenceFault.B
  io.memoryRequest := core.io.ram.en
  io.fenceRequest := core.io.cache.flushData
  io.queueCount := BoringUtils.bore(core.ifid.io.count)
  io.observation.connect(core)
}

// Preserve the small DebugIO interface for simple programs and external traces.
class CoreWrapper(init: ROM.Init, fenceFault: Boolean = false) extends Module {
  def this(initFile: String) = this(ROM.File(initFile))
  def this(words: Seq[BigInt]) = this(ROM.Words(words))

  val io = IO(new DebugIO)
  val system = Module(new CoreMemoryHarness(init, fenceFault = fenceFault))
  system.io.memoryStall := false.B
  system.io.fenceStall := false.B
  io <> system.io.observation.debug
}
