package bpu

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.HasCoreParams
import chisel3.util.Cat


// global history register
class GHR(implicit val p: Parameters) extends Module with HasCoreParams {
  val io = IO(new Bundle {
    // branch information
    val branch  = Input(Bool())
    val taken   = Input(Bool())
    // value of GHR
    val ghr     = Output(UInt(GHR_BITS.W))
  })

  val ghr = Reg(UInt(GHR_BITS.W))

  when (io.branch) {
    ghr := Cat(ghr(GHR_BITS - 2, 0), io.taken)
  }

  io.ghr := ghr
}
