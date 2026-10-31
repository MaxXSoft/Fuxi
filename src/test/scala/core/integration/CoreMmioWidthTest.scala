package core.integration

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import utils.{AxiTestSupport, CoreTester, ExpectedWriteback, InstEncoding,
              InstructionImage, TestDriver, TrapChecker, WritebackChecker}

private[core] class CoreMmioWidthTester(c: CoreFetchFaultWrapper)
    extends CoreTester(c) with AxiTestSupport {
  import InstEncoding._
  val memory = new InstructionImage
  val mmio = BigInt(0x10000000)
  val checks = scala.collection.mutable.ArrayBuffer.empty[ExpectedWriteback]
  val accesses = scala.collection.mutable.ArrayBuffer.empty[(BigInt, Int)]
  var pc = BigInt(0x200)
  def emit(instruction: Long): Unit = { memory.place32(pc, instruction); pc += 4 }
  def load(rd: Int, base: Int, offset: Int, funct3: Int, expected: BigInt): Unit = {
    checks += ExpectedWriteback(pc, rd, expected)
    emit(lb(rd, base, offset) | (funct3.toLong << 12))
    if (base == 8) accesses += ((mmio + offset, funct3 & 3))
  }
  emit(lui(8, 0x10000))
  // Reading IIR must not consume the neighbouring receive-data register.
  load(10, 8, 2, 4, 4)
  load(11, 8, 0, 4, 0x75)
  load(12, 8, 1, 4, 0x81)
  load(13, 8, 3, 0, BigInt("ffffff83", 16))
  load(14, 8, 2, 5, 0x8304)
  load(15, 8, 4, 1, BigInt("ffff8584", 16))
  load(16, 8, 4, 2, BigInt("87868584", 16))
  // Cached byte/halfword loads retain normal lane extraction and extension.
  emit(lui(9, 0x10))
  for (offset <- 0 until 4) load(17 + offset, 9, offset, 4, 0x80 + offset)
  load(21, 9, 3, 0, BigInt("ffffff83", 16))
  load(22, 9, 2, 5, 0x8382)
  load(23, 9, 0, 1, BigInt("ffff8180", 16))
  load(24, 9, 0, 2, BigInt("83828180", 16))
  val donePc = pc
  checks += ExpectedWriteback(pc, 31, 1)
  emit(addi(31, 0, 1))
  emit(jal(0, 0))
  memory.place32(0x10000, BigInt("83828180", 16))
  val writes = new WritebackChecker(checks.toVector)
  val traps = new TrapChecker(Seq.empty)
  Seq(c.io.inst, c.io.data, c.io.uncached).foreach(idleAxi)
  val cached = Seq(c.io.inst, c.io.data).map(port => new AxiReadResponder(port, memory.read32))
  val requests = scala.collection.mutable.ArrayBuffer.empty[(BigInt, Int)]
  var pending: Option[BigInt] = None
  var receiveReads = 0
  var cycle = 0
  runUntil(3500, s"MMIO width; ${writes.missing}") {
    cached.foreach(_.beforeStep(allowAddress = cycle % 3 != 0, allowData = cycle % 5 != 1))
    val port = c.io.uncached
    poke(port.readAddr.ready, pending.isEmpty && cycle % 3 == 1)
    driveAxiRead(port, pending.getOrElse(BigInt(0)), last = true, valid = pending.nonEmpty)
    val acceptedData = pending.nonEmpty && peek(port.readData.ready) != 0
    val acceptedAddress = peek(port.readAddr.valid) != 0 && peek(port.readAddr.ready) != 0
    var nextData: Option[BigInt] = None
    if (acceptedAddress) {
      val address = peek(port.readAddr.bits.addr)
      val size = peek(port.readAddr.bits.size).toInt
      expect(port.readAddr.bits.len, 0)
      requests += ((address, size))
      // AXI narrow reads return each byte in its addressed lane. Device side
      // effects occur only for the bytes actually requested by ARADDR/ARSIZE.
      var data = BigInt(0)
      for (byte <- 0 until (1 << size)) {
        val offset = (address - mmio).toInt + byte
        val value = if (offset == 0) {
          receiveReads += 1
          if (receiveReads == 1) 0x75 else 0
        } else if (offset == 2) 4 else 0x80 + offset
        data |= BigInt(value) << (((address.toInt + byte) & 3) * 8)
      }
      nextData = Some(data)
    }
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val wb = sampleDebug(c.io.observation.debug)
      writes.observe(wb)
      done = wb.pc == donePc
    }
    step(1)
    cached.foreach(_.afterStep())
    if (acceptedData) pending = None
    nextData.foreach(data => pending = Some(data))
    cycle += 1
    done
  }
  writes.checkComplete()
  traps.checkComplete()
  assert(receiveReads == 1, "Reading IIR consumed a receive-data byte")
  assert(requests.toVector == accesses.toVector, "MMIO address or access width changed")
  assert(cached(1).requests.size == 1, "Cached narrow loads should share one cache-line refill")
}

class CoreMmioWidthTest extends AnyFunSuite {
  test("preserve MMIO access widths") {
    TestDriver.run(new CoreFetchFaultWrapper) { c =>
      new CoreMmioWidthTester(c)
    }
  }
}
