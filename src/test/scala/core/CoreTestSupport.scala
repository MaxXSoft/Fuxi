package core

import chisel3._
import consts.Instructions.NOP
import consts.Parameters.RESET_PC
import sim.ROM
import io.DebugIO
import utils.PeekPokeTester

case class ExpectedWriteback(pc: BigInt, rd: Int, data: BigInt)

// Program layout is separate from its dynamic execution expectations.
class CoreProgram(fillWord: BigInt = NOP.litValue) {
  private val image = new utils.InstructionImage(fillWord)
  private val checks = scala.collection.mutable.ArrayBuffer.empty[ExpectedWriteback]
  private var cursor = RESET_PC.litValue

  def words: Seq[BigInt] = {
    val end = image.endAddress.getOrElse(throw new IllegalArgumentException("Empty program"))
    image.words(RESET_PC.litValue, ((end - RESET_PC.litValue + 3) / 4).toInt)
  }
  def expected: Seq[ExpectedWriteback] = checks.toVector
  def pc: BigInt = cursor
  def pcAt(wordIndex: Int): BigInt = RESET_PC.litValue + 4 * wordIndex

  private def checkAddress(address: BigInt, size: Int): Unit =
    require(address >= RESET_PC.litValue && address + size <= pcAt(ROM.DEPTH),
      "Program exceeds ROM capacity")

  def place16(address: BigInt, instruction: Int): Unit = {
    checkAddress(address, 2)
    image.place16(address, instruction)
  }
  def place32(address: BigInt, instruction: Long): Unit = {
    checkAddress(address, 4)
    image.place32(address, instruction)
  }
  def emit16(instruction: Int): Unit = { place16(pc, instruction); cursor += 2 }
  // Keep emit overridable: InstretReadProgram models CSR counter writes here.
  def emit(word: Long): Unit = { place32(pc, word); cursor += 4 }
  def emitAll(words: Seq[Long]): Unit = words.foreach(emit)

  // Legacy word-offset placement preserves one architectural NOP per gap word.
  def seek(wordIndex: Int): Unit = {
    require((pc & 3) == 0 && pcAt(wordIndex) >= pc && wordIndex < ROM.DEPTH,
      "Invalid word program offset")
    while (pc < pcAt(wordIndex)) emit(NOP.litValue.longValue)
  }
  // Sparse placement uses the image's fill pattern and adds no execution events.
  def seekAddress(address: BigInt): Unit = {
    checkAddress(address, 2)
    require((address & 1) == 0 && address >= pc, "Invalid program address")
    cursor = address
  }

  def expectWriteback(rd: Int, data: BigInt): Unit = {
    require(rd > 0 && rd < 32, "Expected writeback must target x1 through x31")
    checks += ExpectedWriteback(pc, rd, data)
  }

  def finish(): BigInt = {
    val donePc = pc
    expectWriteback(31, 1)
    emit(0x00100f93) // addi t6, zero, 1
    emit(0x0000006f) // j .
    donePc
  }
}

// None means that this retirement's writeback is unchecked, not forbidden.
case class ExpectedRetirement(pc: BigInt, write: Option[(Int, BigInt)] = None)
case class ExpectedTrap(pc: BigInt, cause: BigInt, value: BigInt)

// Convenience builder for explicit execution traces. Placing out-of-line code
// never adds events; callers describe branches, repeated calls and traps.
class CoreTraceProgram extends CoreProgram(utils.InstructionImage.CompressedNops) {
  val retired = scala.collection.mutable.ArrayBuffer.empty[ExpectedRetirement]
  val traps = scala.collection.mutable.ArrayBuffer.empty[ExpectedTrap]
  var donePc: BigInt = 0

  def event(address: BigInt, write: Option[(Int, BigInt)] = None): Unit =
    retired += ExpectedRetirement(address, write)
  def half(instruction: Int, write: Option[(Int, BigInt)] = None, retires: Boolean = true): BigInt = {
    val address = pc
    emit16(instruction)
    if (retires) event(address, write)
    address
  }
  def word(instruction: Long, write: Option[(Int, BigInt)] = None, retires: Boolean = true): BigInt = {
    val address = pc
    emit(instruction)
    if (retires) event(address, write)
    address
  }
  override def finish(): BigInt = {
    donePc = word(RiscvTestEncoding.addi(31, 0, 1), Some(31 -> BigInt(1)))
    half(0xa001, retires = false) // c.j .; execution stops at the marker above
    donePc
  }
}

abstract class CoreTester[T <: Module](c: T) extends PeekPokeTester(c) {
  protected def sampleDebug(debug: DebugIO): ObservedInstruction =
    ObservedInstruction(peek(debug.pc), if (peek(debug.regWen) != 0)
      Some(peek(debug.regWaddr).toInt -> peek(debug.regWdata)) else None)

  protected def checkTrap(observation: CoreObservation, checker: TrapChecker): Unit = {
    if (peek(observation.trap) != 0)
      checker.observe(peek(observation.trapPc), peek(observation.trapCause), peek(observation.trapValue))
  }
}

// Check selected writebacks in order, allowing unrelated instructions between them.
class CoreProgramTester(c: CoreWrapper, program: CoreProgram, donePc: BigInt, maxCycles: Int)
    extends CoreTester(c) {
  val checker = new WritebackChecker(program.expected)
  runUntil(maxCycles, s"missing writebacks: ${checker.missing}") {
    step(1)
    val actual = sampleDebug(c.io)
    checker.observe(actual)
    actual.write.nonEmpty && actual.pc == donePc
  }
  checker.checkComplete()
}
