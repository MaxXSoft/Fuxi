package core.stages

import utils.{InstructionImage, PeekPokeTester, TestDriver}
import utils.InstEncoding._

class FetchRasTester(c: FetchHarness) extends PeekPokeTester(c) {
  case class Packet(pc: BigInt, taken: Boolean, target: BigInt, fault: Boolean = false)
  val image = new InstructionImage(InstructionImage.CompressedNops)
  val faults = scala.collection.mutable.Map.empty[BigInt, Int]
  var response = BigInt(0)

  poke(c.io.branch.branch, false)
  poke(c.io.branch.jump, false)
  poke(c.io.branch.taken, false)
  poke(c.io.branch.index, 0)
  poke(c.io.branch.pc, 0)
  poke(c.io.branch.target, 0)

  def tick(stall: Boolean = false, flush: Option[Int] = None,
           contextChange: Boolean = true): Option[Packet] = {
    poke(c.io.stall, stall)
    poke(c.io.flush, flush.nonEmpty)
    poke(c.io.contextFlush, flush.nonEmpty && contextChange)
    poke(c.io.flushPc, flush.getOrElse(0))
    poke(c.io.rom.valid, true)
    poke(c.io.rom.rdata, response)
    val fault = faults.getOrElse(peek(c.io.rom.addr), 0)
    poke(c.io.rom.fault, fault == 1)
    poke(c.io.rom.accessFault, fault == 2)
    val packet = if (peek(c.io.fetch.valid) != 0 && !stall && flush.isEmpty)
      Some(Packet(peek(c.io.fetch.pc), peek(c.io.fetch.taken) != 0,
                  peek(c.io.fetch.target), peek(c.io.fetch.pageFault) != 0 ||
                  peek(c.io.fetch.accessFault) != 0)) else None
    val nextResponse = if (peek(c.io.rom.en) != 0)
      image.read32(peek(c.io.rom.addr)) else response
    step(1)
    response = nextResponse
    packet
  }

  def train(pc: Int, target: Int): Unit = {
    poke(c.io.branch.branch, true)
    poke(c.io.branch.jump, true)
    poke(c.io.branch.taken, true)
    poke(c.io.branch.pc, pc)
    poke(c.io.branch.target, target)
    tick(stall = true, flush = Some(0x200))
    poke(c.io.branch.branch, false)
    poke(c.io.branch.jump, false)
  }

  image.place32(0x200, jal(1, 0x102))
  image.place32(0x302, jal(5, 0x40))
  image.place16(0x342, c_jr(5))
  image.place16(0x306, c_jr(1))
  image.place16(0x204, c_jal(0x70))
  image.place16(0x274, c_jr(1))
  image.place16(0x206, c_jr(1))
  // Both calls hit the BTB, but both returns are cold. Decode remains stalled
  // while the complete nested call/return sequence enters the four-entry FIFO.
  train(0x200, 0x302)
  train(0x302, 0x342)
  train(0x204, 0x274)
  tick(stall = true, flush = Some(0x200))
  for (_ <- 0 until 20) tick(stall = true)
  expect(c.io.queued, 4)
  val expected = Seq(Packet(0x200, true, 0x302), Packet(0x302, true, 0x342),
                     Packet(0x342, true, 0x306), Packet(0x306, true, 0x204),
                     Packet(0x204, true, 0x274), Packet(0x274, true, 0x206),
                     Packet(0x206, false, 0))
  // The next call is held at the frontend while the FIFO is full. It must
  // push only once; the extra return at 0x206 must find the stack empty.
  var seen = 0
  runUntil(80, "Missing nested call/return packets") {
    tick().foreach { packet =>
      assert(packet == expected(seen), s"RAS packet $seen: $packet != ${expected(seen)}")
      seen += 1
    }
    seen == expected.size
  }

  def nextPacket(): Packet = {
    var found: Option[Packet] = None
    runUntil(80, "Missing packet after RAS flush") {
      found = tick()
      found.nonEmpty
    }
    found.get
  }

  image.place32(0x500, jal(1, 0x44))
  image.place16(0x584, c_jr(1))
  train(0x500, 0x544)
  train(0x584, 0x900)
  for (contextChange <- Seq(false, true)) {
    tick(stall = true, flush = Some(0x500))
    for (_ <- 0 until 20) tick(stall = true)
    expect(c.io.queued, 4)
    // The queued call has speculatively pushed 0x504. A decode redirect or
    // global trap/context flush discards it along with all younger packets.
    tick(stall = true, flush = Some(0x584), contextChange = contextChange)
    assert(nextPacket() == Packet(0x584, true, 0x900),
           "Backend flush did not restore the empty-RAS BTB fallback")
  }

  // A fetched return whose word faulted must not use the live RAS entry.
  image.place16(0x544, c_jr(1))
  for (kind <- Seq(1, 2)) {
    faults(0x544) = kind
    tick(flush = Some(0x500))
    assert(nextPacket() == Packet(0x500, true, 0x544))
    assert(nextPacket() == Packet(0x544, false, 0, fault = true))
    for (_ <- 0 until 8) assert(tick().isEmpty, "Faulting return escaped fault blocking")
    faults.clear()
  }
}

object FetchRasTest extends App {
  if (!TestDriver.execute(args, () => new FetchHarness(4)) {
    c => new FetchRasTester(c)
  }) sys.exit(1)
}
