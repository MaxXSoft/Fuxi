package core

import chisel3._
import chisel3.util.Queue
import io._
import utils.{PeekPokeTester, TestDriver}

class FetchHarness(depth: Int) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val contextFlush = Input(Bool())
    val flushPc = Input(UInt(32.W))
    val stall = Input(Bool())
    val branch = Input(new BranchInfoIO)
    val rom = new SramIO(32, 32)
    val fetch = Output(new FetchIO)
    val queued = Output(UInt(8.W))
  })
  val fetch = Module(new Fetch)
  val queue = Module(new Queue(new FetchIO, depth,
                               pipe = true, flow = true, hasFlush = true))
  fetch.io.flush := io.flush
  fetch.io.contextFlush := io.contextFlush
  fetch.io.flushPc := io.flushPc
  fetch.io.stall := !queue.io.enq.ready
  fetch.io.branch := io.branch
  fetch.io.rom <> io.rom
  queue.io.enq.valid := fetch.io.fetch.valid
  queue.io.enq.bits := fetch.io.fetch
  queue.io.deq.ready := !io.stall
  queue.io.flush.get := io.flush
  io.fetch := queue.io.deq.bits
  io.fetch.valid := queue.io.deq.valid
  io.queued := queue.io.count
}

class FetchUnitTester(c: FetchHarness, depth: Int) extends PeekPokeTester(c) {
  case class Packet(pc: BigInt, inst: BigInt, page: Boolean, access: Boolean, fault: BigInt)
  val bytes = scala.collection.mutable.Map.empty[BigInt, Int]
  val faults = scala.collection.mutable.Map.empty[BigInt, Int]
  var response = BigInt(0)
  var heldAddress: Option[BigInt] = None
  var acceptedReads = 0
  def put(pc: BigInt, inst: BigInt, size: Int): Unit =
    for (i <- 0 until size) bytes(pc + i) = ((inst >> (8 * i)) & 255).toInt
  def word(addr: BigInt): BigInt =
    (0 until 4).map(i => BigInt(bytes.getOrElse(addr + i, if (i % 2 == 0) 1 else 0)) << (8 * i)).reduce(_ | _)

  poke(c.io.branch.branch, false)
  poke(c.io.branch.jump, false)
  poke(c.io.branch.taken, false)
  poke(c.io.branch.index, 0)
  poke(c.io.branch.pc, 0)
  poke(c.io.branch.target, 0)

  def tick(stall: Boolean = false, ready: Boolean = true,
           redirect: Option[BigInt] = None, contextChange: Boolean = true): Option[Packet] = {
    poke(c.io.stall, stall)
    poke(c.io.flush, redirect.nonEmpty)
    poke(c.io.contextFlush, redirect.nonEmpty && contextChange)
    poke(c.io.flushPc, redirect.getOrElse(BigInt(0)))
    poke(c.io.rom.rdata, response)
    poke(c.io.rom.valid, ready)
    val addr = peek(c.io.rom.addr)
    val error = faults.getOrElse(addr, 0)
    poke(c.io.rom.fault, error == 1)
    poke(c.io.rom.accessFault, error == 2)
    val enabled = peek(c.io.rom.en) != 0
    heldAddress.foreach { previous =>
      assert(enabled && addr == previous, "Pending SRAM request changed before completion")
    }
    heldAddress = if (enabled && !ready) Some(addr) else None
    val packet = if (peek(c.io.fetch.valid) != 0 && !stall && redirect.isEmpty) {
      Some(Packet(peek(c.io.fetch.pc), peek(c.io.fetch.inst),
                  peek(c.io.fetch.pageFault) != 0, peek(c.io.fetch.accessFault) != 0,
                  peek(c.io.fetch.faultAddr)))
    } else None
    val nextResponse = if (enabled && ready) {
      acceptedReads += 1
      word(addr)
    } else response
    step(1)
    response = nextResponse
    packet
  }

  var address = BigInt(0x200)
  val expected = (0 until 120).map { i =>
    val compressed = i % 3 != 1
    val inst = if (compressed) BigInt(0x0085 + ((i % 15) << 7))
               else BigInt(0x12300513 + ((i % 15) << 7))
    val pc = address
    put(pc, inst, if (compressed) 2 else 4)
    address += (if (compressed) 2 else 4)
    (pc, inst)
  }
  // Fill under backpressure, then verify every packet despite random gaps.
  for (_ <- 0 until 20) tick(stall = true)
  expect(c.io.queued, depth)
  assert(acceptedReads >= depth, "Fetch did not run ahead of stalled Decode")
  val random = new scala.util.Random(0x46555849L)
  var seen = 0
  var cycles = 0
  while (seen < expected.size && cycles < 1500) {
    tick(stall = random.nextInt(4) == 0, ready = random.nextInt(3) != 0).foreach { packet =>
      assert((packet.pc, packet.inst) == expected(seen), s"Packet $seen: $packet != ${expected(seen)}")
      assert(!packet.page && !packet.access)
      seen += 1
    }
    cycles += 1
  }
  assert(seen == expected.size)

  def nextPacket(): Packet = {
    var found: Option[Packet] = None
    var n = 0
    while (found.isEmpty && n < 100) { found = tick(); n += 1 }
    assert(found.nonEmpty, "No instruction after redirect")
    found.get
  }

  // A split instruction's second parcel can fail independently of its PC.
  for (kind <- Seq(1, 2)) {
    put(0xffe, 0x12300513, 4)
    faults(0x1000) = kind
    tick(redirect = Some(0xffe))
    val packet = nextPacket()
    assert(packet.pc == 0xffe && packet.fault == 0x1000)
    assert(packet.page == (kind == 1) && packet.access == (kind == 2))
    for (_ <- 0 until 8) assert(tick().isEmpty, "Fault packet repeated")
    faults.clear()
  }
  // A compressed last-halfword instruction needs no next-page permission.
  put(0xffe, 0x0085, 2)
  faults(0x1000) = 1
  tick(redirect = Some(0xffe))
  val lastHalfword = nextPacket()
  assert(lastHalfword.pc == 0xffe && !lastHalfword.page && !lastHalfword.access)
  val following = nextPacket()
  assert(following.pc == 0x1000 && following.page && following.fault == 0x1000)
  faults.clear()

  // Kill an unaccepted request, then return its access fault after redirect.
  tick(redirect = Some(0x600))
  tick(stall = true, ready = false)
  val oldAddress = peek(c.io.rom.addr)
  faults(oldAddress) = 2
  tick(stall = true, ready = false, redirect = Some(0x704))
  tick(stall = true, ready = false, redirect = Some(0x702))
  for (_ <- 0 until 3) tick(stall = true, ready = false)
  put(0x702, 0x0085, 2)
  tick(stall = true)
  faults.clear()
  val redirected = nextPacket()
  assert(redirected.pc == 0x702 && redirected.inst == 0x0085 && !redirected.access)

  // BTB steering keeps older FIFO entries, but skips sequential younger data.
  put(0x802, 0xa001, 2)
  put(0x906, 0x0085, 2)
  poke(c.io.branch.branch, true)
  poke(c.io.branch.jump, true)
  poke(c.io.branch.taken, true)
  poke(c.io.branch.pc, 0x802)
  poke(c.io.branch.target, 0x906)
  tick(redirect = Some(0x800))
  poke(c.io.branch.branch, false)
  poke(c.io.branch.jump, false)
  for (_ <- 0 until 12) tick(stall = true)
  val predicted = Seq(nextPacket(), nextPacket(), nextPacket())
  assert(predicted.map(_.pc) == Seq(0x800, 0x802, 0x906))

  // A predicted target can use an idle request port without a redirect bubble.
  poke(c.io.branch.branch, true)
  poke(c.io.branch.jump, true)
  poke(c.io.branch.pc, 0xa00)
  poke(c.io.branch.target, 0xb00)
  put(0xa00, 0x1000006f, 4)
  put(0xb00, 0x0085, 2)
  tick(stall = true, redirect = Some(0xa00))
  poke(c.io.branch.branch, false)
  poke(c.io.branch.jump, false)
  assert(nextPacket().pc == 0xa00)
  assert(tick().exists(_.pc == 0xb00), "Unnecessary predicted-target fetch bubble")

  // The reported VA is the instruction start, not its aligned fetch word.
  faults(0xc00) = 1
  tick(redirect = Some(0xc02))
  val firstParcelFault = nextPacket()
  assert(firstParcelFault.pc == 0xc02 && firstParcelFault.page && firstParcelFault.fault == 0xc02)

  // xRET/CSR flushes change the MMU context at the edge, unlike predictions.
  // Do not accept their target using the outgoing translation/privilege state.
  for (_ <- 0 until 8) tick()
  expect(c.io.rom.en, false)
  faults.clear()
  poke(c.io.flush, true)
  poke(c.io.contextFlush, true)
  poke(c.io.flushPc, 0xd02)
  expect(c.io.rom.en, false)
  tick(redirect = Some(0xd02))
  assert(nextPacket().pc == 0xd02)

  // Decode branch recovery can still steer immediately without changing MMU state.
  poke(c.io.flush, true)
  poke(c.io.contextFlush, false)
  poke(c.io.flushPc, 0xe02)
  expect(c.io.rom.addr, 0xe00)
  tick(redirect = Some(0xe02), contextChange = false)
  assert(tick().exists(_.pc == 0xe02), "Unnecessary decode-target fetch bubble")
}

object FetchTest extends App {
  for (depth <- Seq(1, 4)) {
    if (!TestDriver.execute(args, () => new FetchHarness(depth)) {
      c => new FetchUnitTester(c, depth)
    }) sys.exit(1)
  }
}
