package consts

// Select one event with group in bits 31:24 and local event ID in bits 23:0.
// Group zero is reserved: the all-zero selector disables counting.
object PerfEvent {
  val GROUP_WIDTH = 8
  val EVENT_WIDTH = 24

  object Group extends Enumeration {
    val RETIRED = Value(1)
    val MICROARCHITECTURE = Value(2)
    val MEMORY = Value(3)
  }

  // Append within each group to preserve the other groups' software-visible IDs.
  object Retired extends Enumeration {
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
  }

  object Microarchitecture extends Enumeration {
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
  }

  object Memory extends Enumeration {
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
  }

  // This registry defines the Core/CSR interface and legal selector encodings.
  val groups: Seq[(Group.Value, Enumeration)] = Seq(
    Group.RETIRED -> Retired,
    Group.MICROARCHITECTURE -> Microarchitecture,
    Group.MEMORY -> Memory,
  )

  require(Group.values.forall(g => g.id > 0 && BigInt(g.id) < (BigInt(1) << GROUP_WIDTH)),
          "PMU group IDs must fit 8 bits; group zero is reserved for disabling counters")
  require(groups.map(_._1).toSet == Group.values.toSet && groups.size == Group.values.size,
          "Every PMU group must have exactly one event enumeration")
  for ((group, events) <- groups) {
    require(events.values.nonEmpty &&
            events.values.forall(e => e.id >= 0 && BigInt(e.id) < (BigInt(1) << EVENT_WIDTH)),
            s"PMU event IDs in $group must fit 24 bits")
    require(events.values.toSeq.map(_.id) == events.values.toSeq.indices,
            s"PMU event IDs in $group must be contiguous from zero for vector indexing")
  }

  def encode(group: Group.Value, event: Enumeration#Value): BigInt = {
    require(groups.exists { case (g, events) => g == group && events.values.exists(_ == event) },
            s"Event $event does not belong to PMU group $group")
    (BigInt(group.id) << EVENT_WIDTH) | BigInt(event.id)
  }
}
