package bpu

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.CoreParamsKey
import chisel3.util.Cat


// global history register
class GHR(implicit val p: Parameters) extends Module {
  val params = p(CoreParamsKey).bpu

  val io = IO(new Bundle {
    // branch information
    val branch  = Input(Bool())
    val taken   = Input(Bool())
    // value of GHR
    val ghr     = Output(UInt(params.ghrBits.W))
  })

  val ghr = Reg(UInt(params.ghrBits.W))

  when (io.branch) {
    ghr := Cat(ghr(params.ghrBits - 2, 0), io.taken)
  }

  io.ghr := ghr
}
