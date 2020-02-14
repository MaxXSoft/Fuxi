import chisel3.{Module, Bundle, Driver}

import io._
import axi.AxiMaster
import consts.Parameters.{ADDR_WIDTH, DATA_WIDTH}

import core.Core
import bus.CoreBus

class Fuxi extends Module {
  val io = IO(new Bundle {
    // interrupt request
    val irq       = new InterruptIO
    // for trace generating
    val debug     = new DebugIO
    // AXI interfaces
    val inst      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val data      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val uncached  = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })

  val core    = Module(new Core)
  val coreBus = Module(new CoreBus)

  core.io.irq   <> io.irq
  core.io.tlb   <> coreBus.io.tlb
  core.io.cache <> coreBus.io.cache
  core.io.rom   <> coreBus.io.rom
  core.io.ram   <> coreBus.io.ram
  core.io.debug <> io.debug

  coreBus.io.inst     <> io.inst
  coreBus.io.data     <> io.data
  coreBus.io.uncached <> io.uncached
}

object Fuxi extends App {
  Driver.execute(args, () => new Fuxi)
}
