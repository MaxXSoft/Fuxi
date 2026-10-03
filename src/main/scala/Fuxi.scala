import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.ChiselStage

import io._
import axi.AxiMaster
import consts.Parameters._

import core.Core
import bus.CoreBus

class Fuxi(hpmCount: Int) extends Module {
  val io = IO(new Bundle {
    // interrupt request
    val irq       = new InterruptIO
    // The platform timebase, shared with the memory-mapped timer.
    val rtcTime   = Input(UInt(64.W))
    // for trace generating
    val debug     = new DebugIO
    // AXI interfaces
    val inst      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val data      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val uncached  = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })

  val core    = Module(new Core(FETCH_QUEUE_DEPTH, hpmCount))
  val coreBus = Module(new CoreBus)

  core.io.irq   <> io.irq
  core.io.rtcTime := io.rtcTime
  core.io.tlb   <> coreBus.io.tlb
  core.io.cache <> coreBus.io.cache
  core.io.rom   <> coreBus.io.rom
  core.io.ram   <> coreBus.io.ram
  core.io.debug <> io.debug
  core.io.pmu   := coreBus.io.pmu

  coreBus.io.inst     <> io.inst
  coreBus.io.data     <> io.data
  coreBus.io.uncached <> io.uncached
}

object Fuxi extends App {
  private val HPM_COUNTERS = "--hpm-counters"

  private def invalidOption(message: String): Nothing = {
    Console.err.println(s"Fuxi: $message")
    sys.exit(1)
  }

  private val firtoolOptions = Array(
    "--firtool-option", "-disable-all-randomization",
    "--firtool-option", "-strip-debug-info",
    "--firtool-option", "-default-layer-specialization=enable",
    "--firtool-option", "-lowering-options=disallowLocalVariables,disallowPackedArrays," +
      "disallowPackedStructAssignments,disallowDeclAssignments,noAlwaysComb," +
      "mitigateVivadoArrayIndexConstPropBug"
  )

  // Keep the default build small; zero is useful for synthesis comparisons.
  if (args.count(_ == HPM_COUNTERS) > 1) {
    invalidOption(s"$HPM_COUNTERS may be given at most once")
  }
  private val hpmOption = args.indexOf(HPM_COUNTERS)
  private val hpmCount = if (hpmOption < 0) HPM_COUNT else {
    if (hpmOption + 1 == args.length) {
      invalidOption(s"$HPM_COUNTERS requires a count from 0 to 29")
    }
    val raw = args(hpmOption + 1)
    raw.toIntOption.filter(n => n >= 0 && n <= 29).getOrElse(
      invalidOption(s"$HPM_COUNTERS expects an integer from 0 to 29, got '$raw'"))
  }
  private val stageArgs = if (hpmOption < 0) args else args.patch(hpmOption, Nil, 2)

  (new ChiselStage).execute(
    Array("--target", "verilog") ++ firtoolOptions ++ stageArgs,
    Seq(ChiselGeneratorAnnotation(() => new Fuxi(hpmCount)))
  )
}
