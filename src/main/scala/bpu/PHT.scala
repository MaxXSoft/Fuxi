package bpu

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.HasCoreParams


// pattern history table
class PHT(implicit val p: Parameters) extends Module with HasCoreParams {
  val io = IO(new Bundle {
    // branch information (from decoder)
    val lastBranch  = Input(Bool())
    val lastTaken   = Input(Bool())
    val lastIndex   = Input(UInt(GHR_BITS.W))
    // index for looking up counter table
    val index       = Input(UInt(GHR_BITS.W))
    // prediction result
    val taken       = Output(Bool())
  })

  // 2-bit saturation counters
  val init      = Seq.fill(PHT_ENTRIES) { "b10".U(2.W) }
  val counters  = RegInit(VecInit(init))

  // update counter
  when (io.lastBranch) {
    when (counters(io.lastIndex) === "b11".U) {
      when (!io.lastTaken) {
        counters(io.lastIndex) := counters(io.lastIndex) - 1.U
      }
    } .elsewhen (counters(io.lastIndex) === "b00".U) {
      when (io.lastTaken) {
        counters(io.lastIndex) := counters(io.lastIndex) + 1.U
      }
    } .otherwise {
      // counter === b01 || counter === b10
      when (!io.lastTaken) {
        counters(io.lastIndex) := counters(io.lastIndex) - 1.U
      } .otherwise {
        counters(io.lastIndex) := counters(io.lastIndex) + 1.U
      }
    }
  }

  // generate output
  io.taken := counters(io.index)(1)
}
