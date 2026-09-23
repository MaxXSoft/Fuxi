package core

import chisel3._
import io.DebugIO
import sim.{RAM, ROM}

class CoreWrapper(init: ROM.Init, fenceFault: Boolean = false) extends Module {
  def this(initFile: String) = this(ROM.File(initFile))
  def this(words: Seq[BigInt]) = this(ROM.Words(words))

  val io    = IO(new DebugIO)
  val core  = Module(new Core)
  val rom   = Module(new ROM(init))
  val ram   = Module(new RAM)

  core.io.irq.timer   := false.B
  core.io.irq.soft    := false.B
  core.io.irq.extern  := false.B
  core.io.cache.flushDataDone        := true.B
  core.io.cache.flushDataAccessFault := fenceFault.B
  core.io.rom         <> rom.io
  core.io.ram         <> ram.io
  core.io.debug       <> io
}

