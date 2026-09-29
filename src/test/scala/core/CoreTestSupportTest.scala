package core

import consts.Parameters.RESET_PC
import sim.ROM
import utils.InstructionImage

// Pure Scala regressions for layout contracts that RTL tests otherwise obscure.
object CoreTestSupportTest extends App {
  def rejects(body: => Unit): Unit = {
    var rejected = false
    try body catch { case _: IllegalArgumentException => rejected = true }
    assert(rejected, "Expected invalid layout to be rejected")
  }

  val base = RESET_PC.litValue
  val mixed = new CoreProgram(InstructionImage.CompressedNops)
  mixed.emit16(InstEncoding.c_nop())
  mixed.expectWriteback(10, 42)
  mixed.emit32(InstEncoding.addi(10, 0, 42))
  mixed.emit16(0x0000) // Deliberately illegal instructions are valid image data.
  assert(mixed.words == Seq(BigInt(0x05130001), BigInt(0x000002a0)))
  assert(mixed.expected == Seq(ExpectedWriteback(base + 2, 10, 42)))
  assert(mixed.pc == base + 8)
  mixed.place16(base + 0x20, InstEncoding.c_addi(1, 1))
  assert(mixed.pc == base + 8)
  assert(mixed.words(2) == InstructionImage.CompressedNops)
  assert(mixed.words(8) == 0x00010085)

  val legacy = new CoreProgram
  legacy.emit32(InstEncoding.nop())
  legacy.seekWord(3)
  assert(legacy.pc == legacy.pcAtWord(3))
  assert(legacy.words == Seq(BigInt(0x13))) // Seeking does not emit padding instructions.
  val marker = legacy.finish()
  assert(legacy.words.take(3) == Seq.fill(3)(BigInt(0x13)))
  assert(legacy.pc == marker + 6)
  assert((legacy.words.last & 0xffff) == 0xa001)
  legacy.seekWord(8) // A compressed tail leaves a halfword-aligned cursor.
  legacy.emit32(InstEncoding.addi(0, 0, 0))
  assert(marker == base + 12 && legacy.expected.last == ExpectedWriteback(marker, 31, 1))

  val tracedEnd = new CoreTraceProgram
  val tracedMarker = tracedEnd.finish()
  assert(tracedEnd.pc == tracedMarker + 6)
  assert(tracedEnd.words.head == legacy.words(3))
  assert((tracedEnd.words.last & 0xffff) == 0xa001)
  assert(tracedEnd.retired.toVector == Seq(ExpectedRetirement(tracedMarker, Some(31 -> BigInt(1)))))

  val edge = new CoreProgram
  edge.seekAddress(base + ROM.DEPTH * 4 - 2)
  rejects(edge.emit32(InstEncoding.nop()))
  edge.emit16(InstEncoding.c_nop())
  assert(edge.words.size == ROM.DEPTH)
  rejects(edge.emit16(InstEncoding.c_nop()))
  rejects(new CoreProgram().words)
  rejects(mixed.seekAddress(base + 9))
  rejects(mixed.place32(base + 1, InstEncoding.nop()))
  rejects(mixed.place16(base, InstEncoding.c_nop()))
  rejects(mixed.place32(base + 0x40, 1L << 32))
  rejects(mixed.place16(base + 0x40, -1))

  val sparse = new InstructionImage
  val high = BigInt("80000000", 16)
  sparse.place16(high + 4, 0xbeef)
  rejects(sparse.place32(high + 2, 0x12345678))
  assert(sparse.read32(high) == 0) // Failed overlapping writes must be atomic.
  assert(sparse.read32(high + 4) == 0xbeef)
  sparse.replace32(high + 2, 0x12345678)
  assert(sparse.read32(high) == BigInt("56780000", 16))
  assert(sparse.read32(high + 4) == 0x1234)

  val trace = new CoreTraceProgram
  trace.half(InstEncoding.c_nop())
  trace.word(InstEncoding.nop(), retires = false)
  trace.event(0x380)
  trace.event(0x380, Some(6 -> BigInt(2)))
  trace.place32(0x380, InstEncoding.nop())
  assert(trace.retired.map(_.pc).toSeq == Seq(base, BigInt(0x380), BigInt(0x380)))
  assert(trace.pc == base + 6)
  def fails(body: => Unit): Unit = {
    var failed = false
    try body catch { case _: AssertionError => failed = true }
    assert(failed, "Expected a mismatched execution event to fail")
  }
  // Preserve PeekPokeTester's signed-value normalization without silently
  // truncating an oversized positive expected value.
  fails(CoreChecks.writeback(ObservedInstruction(base, Some(6 -> BigInt(1))),
    ExpectedWriteback(base, 6, (BigInt(1) << 32) + 1)))
  val writes = new WritebackChecker(Seq(ExpectedWriteback(base, 6, -1), ExpectedWriteback(base + 4, 7, 2)))
  writes.observe(ObservedInstruction(base + 8, Some(8 -> BigInt(99)))) // Unselected writeback.
  writes.observe(ObservedInstruction(base, None))
  fails(writes.checkComplete())
  fails(writes.observe(ObservedInstruction(base + 4, Some(7 -> BigInt(2)))))
  writes.observe(ObservedInstruction(base, Some(6 -> BigInt("ffffffff", 16))))
  fails(writes.observe(ObservedInstruction(base, Some(6 -> BigInt(0)))))
  writes.observe(ObservedInstruction(base + 4, Some(7 -> BigInt(2))))
  writes.checkComplete()
  fails(writes.observe(ObservedInstruction(base + 4, Some(7 -> BigInt(2)))))
  rejects(new WritebackChecker(Seq.fill(2)(ExpectedWriteback(base, 6, 1))))

  val retirements = new RetirementChecker(Seq(
    ExpectedRetirement(base), ExpectedRetirement(base, Some(6 -> BigInt(2)))))
  retirements.checkCount(0)
  retirements.observe(ObservedInstruction(base, Some(9 -> BigInt(99)))) // None is unchecked.
  retirements.checkCount(1)
  fails(retirements.checkCount(0))
  fails(retirements.checkComplete())
  fails(retirements.observe(ObservedInstruction(base + 4, None)))
  fails(retirements.observe(ObservedInstruction(base, None)))
  retirements.observe(ObservedInstruction(base, Some(6 -> BigInt(2))))
  retirements.checkComplete()
  retirements.checkCount(2)
  fails(retirements.observe(ObservedInstruction(base, None)))

  val traps = new TrapChecker(Seq(ExpectedTrap(base, 12, 0x1000)))
  fails(traps.checkComplete())
  fails(traps.observe(base, 12, base))
  traps.observe(base, 12, 0x1000)
  traps.checkComplete()
  fails(traps.observe(base, 12, 0x1000))
  println("CoreTestSupport: layout and event-checker contracts passed.")
}
