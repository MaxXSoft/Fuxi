package io

import chisel3._

// These signals only observe local activity; no performance-monitor signal
// participates in the request, ready/valid, or prediction control paths.
class CachePerformanceIO extends Bundle {
  // SRAM en && valid transfers, including D$ writes and fault responses
  // only while the requester keeps en asserted (the LSU drops en on fault).
  // A refill contributes only when its waiting request eventually completes.
  val access = Bool()
  // New miss accepted by the idle cache, before any eviction or refill wait.
  // Speculative requests canceled later still contribute a miss.
  val miss = Bool()
  // Every non-idle cycle, including refill completion/fault and maintenance.
  val busy = Bool()
  // Accepted AXI write address: one per dirty eviction/maintenance burst.
  val writeback = Bool()
}

class MmuPerformanceIO extends Bundle {
  val miss = Bool() // new page-table walk, once across all levels and stalls
  val walkCycle = Bool() // address, PTE interpretation, fill/fault; excludes flush
}

class RasPerformanceIO extends Bundle {
  val overflow = Bool() // accepted push discards oldest entry
  val emptyPop = Bool() // accepted pop finds empty stack, including pop/push
  val clearNonempty = Bool() // backend flush discards nonempty stack
}

class BusPerformanceIO extends Bundle {
  // Cache totals include translated program requests and page-table traffic.
  // Walk subsets identify the SRAM owner at completion (access) or launch
  // (miss); subtract them from totals to obtain program-side activity.
  val icacheAccess = Bool()
  val icacheMiss = Bool()
  val icacheBusy = Bool()
  val icacheWalkAccess = Bool()
  val icacheWalkMiss = Bool()
  val dcacheAccess = Bool()
  val dcacheMiss = Bool()
  val dcacheBusy = Bool()
  val dcacheWriteback = Bool()
  val dcacheWalkAccess = Bool()
  val dcacheWalkMiss = Bool()
  val itlbMiss = Bool()
  val itlbWalkCycle = Bool()
  val dtlbMiss = Bool()
  val dtlbWalkCycle = Bool()
}
