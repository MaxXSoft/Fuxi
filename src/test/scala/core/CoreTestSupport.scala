package core

import chisel3._
import consts.Instructions.NOP
import consts.Parameters.RESET_PC
import sim.ROM
import utils.PeekPokeTester

case class ExpectedWriteback(pc: BigInt, rd: Int, data: BigInt)

// A small instruction-image builder; ISA-specific reference models stay in each test.
class CoreProgram {
  private val instructions = scala.collection.mutable.ArrayBuffer.empty[BigInt]
  private val checks = scala.collection.mutable.ArrayBuffer.empty[ExpectedWriteback]

  def words: Seq[BigInt] = instructions.toVector
  def expected: Seq[ExpectedWriteback] = checks.toVector
  def pc: BigInt = pcAt(instructions.size)
  def pcAt(wordIndex: Int): BigInt = RESET_PC.litValue + 4 * wordIndex

  def emit(word: Long): Unit = {
    require(instructions.size < ROM.DEPTH, "Program exceeds ROM capacity")
    require(word >= 0 && BigInt(word) < (BigInt(1) << 32), "Invalid instruction word")
    instructions += BigInt(word)
  }

  def emitAll(words: Seq[Long]): Unit = words.foreach(emit)

  // Place subsequent code at a word offset, padding the gap with architectural NOPs.
  def seek(wordIndex: Int): Unit = {
    require(wordIndex >= instructions.size && wordIndex < ROM.DEPTH, "Invalid program offset")
    while (instructions.size < wordIndex) emit(NOP.litValue.longValue)
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

abstract class CoreTester[T <: Module](c: T) extends PeekPokeTester(c) {
  // The caller steps exactly once per iteration and chooses whether to sample
  // before or after that edge. This preserves retirement counter timing checks.
  protected def runUntil(maxCycles: Int, context: => String)(cycle: => Boolean): Unit = {
    require(maxCycles > 0)
    var done = false
    var cycles = 0
    while (cycles < maxCycles && !done) {
      done = cycle
      cycles += 1
    }
    assert(done, s"Timed out after $maxCycles cycles: $context")
  }
}

// Check selected writebacks in order, allowing unrelated instructions between them.
class CoreProgramTester(c: CoreWrapper, program: CoreProgram, donePc: BigInt, maxCycles: Int)
    extends CoreTester(c) {
  val pending = scala.collection.mutable.Queue.from(program.expected)
  val checkedPcs = program.expected.map(_.pc).toSet
  require(checkedPcs.size == program.expected.size, "Duplicate writeback checkpoint PC")

  runUntil(maxCycles, s"missing writebacks: ${pending.mkString(", ")}") {
    step(1)
    val writes = peek(c.io.regWen) != 0
    val pc = peek(c.io.pc)
    if (writes && checkedPcs(pc)) {
      assert(pending.nonEmpty, s"Repeated writeback at PC 0x${pc.toString(16)}")
      val expected = pending.dequeue()
      expect(c.io.pc, expected.pc)
      expect(c.io.regWaddr, expected.rd)
      expect(c.io.regWdata, expected.data)
    }
    writes && pc == donePc
  }
  assert(pending.isEmpty, s"Missing writebacks: ${pending.mkString(", ")}")
}
