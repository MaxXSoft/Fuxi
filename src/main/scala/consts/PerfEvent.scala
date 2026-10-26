package consts

// Fuxi PMU event ABI. Select one ID per mhpmeventN; zero disables counting.
// IDs describe event pulses (or active cycles), not counter numbers.
object PerfEvent extends Enumeration {
  // Append new events: these IDs are a software-visible ABI.
  val DISABLED = Value
  val BRANCH = Value
  val BRANCH_MISS = Value
  val BRANCH_DIRECTION_MISS = Value
  val BRANCH_TARGET_MISS = Value
  val JAL = Value
  val JAL_MISS = Value
  val JALR = Value
  val JALR_MISS = Value
  val RAS_CANDIDATE = Value
  val RAS_USED = Value
  val RAS_MISS = Value
  val RAS_FALLBACK_MISS = Value
  val OTHER_JALR = Value
  val OTHER_JALR_MISS = Value
  val COMPRESSED = Value
  val LOAD = Value
  val STORE = Value
  val FRONTEND_EMPTY = Value
  val LOAD_USE_STALL = Value
  val MDU_STALL = Value
  val MEM_STALL = Value
  val CSR_STALL = Value
  val FETCH_QUEUE_FULL = Value
  val DECODE_REDIRECT = Value
  val CONTEXT_FLUSH = Value
  val RAS_OVERFLOW = Value
  val RAS_EMPTY_POP = Value
  val RAS_CLEAR_MISPREDICT = Value
  val RAS_CLEAR_CONTEXT = Value
  val ICACHE_ACCESS = Value
  val ICACHE_MISS = Value
  val ICACHE_BUSY = Value
  val ICACHE_WALK_ACCESS = Value
  val ICACHE_WALK_MISS = Value
  val DCACHE_ACCESS = Value
  val DCACHE_MISS = Value
  val DCACHE_BUSY = Value
  val DCACHE_WRITEBACK = Value
  val DCACHE_WALK_ACCESS = Value
  val DCACHE_WALK_MISS = Value
  val ITLB_MISS = Value
  val ITLB_WALK_CYCLE = Value
  val DTLB_MISS = Value
  val DTLB_WALK_CYCLE = Value

  require(values.size > 1 && values.size <= 64)
}
