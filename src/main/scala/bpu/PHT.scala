package bpu

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.HasCoreParams
import chisel3.util.Cat


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

  // Compute both saturating transitions locally. The late branch result
  // selects two bits without a shared indexed read/modify/write path.
  for (i <- 0 until PHT_ENTRIES) {
    val hi = counters(i)(1)
    val lo = counters(i)(0)
    val increment = Cat(hi || lo, hi || !lo)
    val decrement = Cat(hi && lo, hi && !lo)
    when (io.lastBranch && io.lastIndex === i.U) {
      counters(i) := Mux(io.lastTaken, increment, decrement)
    }
  }

  // generate output
  io.taken := counters(io.index)(1)
}
