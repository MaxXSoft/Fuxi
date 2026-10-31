import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.ConfigDump
import cli.{CliExit, GeneratorOptions}
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.ChiselStage

import io._
import axi.AxiMaster
import consts.Architecture._

import core.Core
import bus.CoreBus

class Fuxi(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    // interrupt request
    val irq       = new InterruptIO
    // The platform timebase, shared with the memory-mapped timer.
    val rtcTime   = Input(UInt(RTC_WIDTH.W))
    // for trace generating
    val debug     = new DebugIO
    // AXI interfaces
    val inst      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val data      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    val uncached  = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })

  val core    = Module(new Core)
  val coreBus = Module(new CoreBus)

  core.io.irq     <> io.irq
  core.io.rtcTime := io.rtcTime
  core.io.tlb     <> coreBus.io.tlb
  core.io.cache   <> coreBus.io.cache
  core.io.rom     <> coreBus.io.rom
  core.io.ram     <> coreBus.io.ram
  core.io.debug   <> io.debug
  core.io.pmu     := coreBus.io.pmu

  coreBus.io.inst     <> io.inst
  coreBus.io.data     <> io.data
  coreBus.io.uncached <> io.uncached
}

object Fuxi extends App {
  private val firtoolOptions = Array(
    "--firtool-option", "-disable-all-randomization",
    "--firtool-option", "-strip-debug-info",
    "--firtool-option", "-default-layer-specialization=enable",
    "--firtool-option", "-lowering-options=disallowLocalVariables,disallowPackedArrays," +
      "disallowPackedStructAssignments,disallowDeclAssignments,noAlwaysComb," +
      "mitigateVivadoArrayIndexConstPropBug",
  )

  GeneratorOptions.parse(args.toSeq) match {
    case Left(exit) => exit.report()
    case Right(options) => options.config.resolve() match {
      case Left(error) => CliExit(1, s"Fuxi: $error").report()
      case Right(parameters) =>
        if (options.dumpConfig) {
          println(ConfigDump(parameters))
        } else {
          val target = options.targetDir.toSeq.flatMap(dir => Seq("--target-dir", dir))
          (new ChiselStage).execute(
            Array("--target", "verilog") ++ firtoolOptions ++ target ++ options.stageArgs,
            Seq(ChiselGeneratorAnnotation(() => new Fuxi()(parameters)))
          )
        }
    }
  }
}
