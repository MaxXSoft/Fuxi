package consts

// Fixed by the implemented RV32IMAC/Sv32 architecture and external interfaces.
// Tunable microarchitecture and platform values live in configs instead.
object Architecture {
  val ADDR_WIDTH = 32
  val ADDR_ALIGN_WIDTH = 2
  val DATA_WIDTH = 32
  val INST_WIDTH = 32
  val INST_ALIGN_WIDTH = 1
  val REG_COUNT = 32
  val REG_ADDR_WIDTH = 5
  val RTC_WIDTH = 64
}
