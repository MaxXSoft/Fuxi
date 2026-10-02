package consts

// Fuxi PMU event ABI. Select one ID per mhpmeventN; zero disables counting.
// IDs describe event pulses (or active cycles), not counter numbers.
object PerfEvent {
  val EVENT_COUNT = 45
  val BRANCH = 1
  val BRANCH_MISS = 2
  val BRANCH_DIRECTION_MISS = 3
  val BRANCH_TARGET_MISS = 4
  val JAL = 5
  val JAL_MISS = 6
  val JALR = 7
  val JALR_MISS = 8
  val RAS_CANDIDATE = 9
  val RAS_USED = 10
  val RAS_MISS = 11
  val RAS_FALLBACK_MISS = 12
  val OTHER_JALR = 13
  val OTHER_JALR_MISS = 14
  val COMPRESSED = 15
  val LOAD = 16
  val STORE = 17
  val FRONTEND_EMPTY = 18
  val LOAD_USE_STALL = 19
  val MDU_STALL = 20
  val MEM_STALL = 21
  val CSR_STALL = 22
  val FETCH_QUEUE_FULL = 23
  val DECODE_REDIRECT = 24
  val CONTEXT_FLUSH = 25
  val RAS_OVERFLOW = 26
  val RAS_EMPTY_POP = 27
  val RAS_CLEAR_MISPREDICT = 28
  val RAS_CLEAR_CONTEXT = 29
  val ICACHE_ACCESS = 30
  val ICACHE_MISS = 31
  val ICACHE_BUSY = 32
  val ICACHE_WALK_ACCESS = 33
  val ICACHE_WALK_MISS = 34
  val DCACHE_ACCESS = 35
  val DCACHE_MISS = 36
  val DCACHE_BUSY = 37
  val DCACHE_WRITEBACK = 38
  val DCACHE_WALK_ACCESS = 39
  val DCACHE_WALK_MISS = 40
  val ITLB_MISS = 41
  val ITLB_WALK_CYCLE = 42
  val DTLB_MISS = 43
  val DTLB_WALK_CYCLE = 44
}
