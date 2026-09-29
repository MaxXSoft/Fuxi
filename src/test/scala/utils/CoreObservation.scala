package utils

import chisel3._
import core.Core
import chisel3.util.experimental.BoringUtils
import consts.Parameters._
import io.DebugIO

// Test-only observation of retirement and trap boundaries. Architectural return
// operations retire normally and are excluded from the trap event stream.
class CoreObservation extends Bundle {
  val debug = new DebugIO
  val retired = Output(Bool())
  val count = Output(UInt(64.W))
  val trap = Output(Bool())
  val trapPc = Output(UInt(ADDR_WIDTH.W))
  val trapCause = Output(UInt(DATA_WIDTH.W))
  val trapValue = Output(UInt(DATA_WIDTH.W))

  // Call from the harness after this bundle is bound through IO.
  def connect(core: Core): Unit = {
    debug <> core.io.debug
    retired := BoringUtils.bore(core.wb.io.csr.retired)
    count := BoringUtils.bore(core.csrfile.minstret.data)
    val except = BoringUtils.bore(core.mem.io.except)
    trap := except.hasTrap && !except.isMret && !except.isSret
    trapPc := except.excPc
    trapCause := except.excCause
    trapValue := except.excValue
  }
}
