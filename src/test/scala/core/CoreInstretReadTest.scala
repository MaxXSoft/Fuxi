package core

import chisel3._
import consts.CSR._
import consts.Parameters.RESET_PC
import java.io.{File, PrintWriter}
import utils.{PeekPokeTester, TestDriver}

object InstretReadProgram {
  val words = scala.collection.mutable.ArrayBuffer.empty[Long]
  val expected = scala.collection.mutable.Map.empty[BigInt, BigInt]
  private val mask32 = (BigInt(1) << 32) - 1
  private var count = BigInt(0)

  def emit(word: Long): Unit = { words += word; count += 1 }
  def high(addr: UInt): Boolean = (addr.litValue & 0x80) != 0
  def value(addr: UInt): BigInt = if (high(addr)) count >> 32 else count & mask32
  def assign(addr: UInt, data: BigInt): Unit = {
    count = if (high(addr)) ((data & mask32) << 32) | (count & mask32)
            else (count & ~mask32) | (data & mask32)
  }
  def read(addr: UInt): Unit = {
    expected(RESET_PC.litValue + 4 * words.size) = value(addr)
    emit((addr.litValue.toLong << 20) | 0x2373) // csrr t1, addr
  }
  def modify(addr: UInt, data: Int, operation: Int = 1, returnOld: Boolean = false): Unit = {
    emit(((data & 0xfff).toLong << 20) | 0x293) // li t0, signed 12-bit data
    val old = value(addr)
    if (returnOld) expected(RESET_PC.litValue + 4 * words.size) = old
    words += ((addr.litValue.toLong << 20) | (5L << 15) | (operation << 12) |
      (if (returnOld) 6L << 7 else 0L) | 0x73)
    val operand = BigInt(data) & mask32
    assign(addr, if (operation == 1) operand
                 else if (operation == 2) old | operand else old & ~operand)
  }

  modify(CSR_MINSTRETH, 0)
  modify(CSR_MINSTRET, 0)
  read(CSR_MINSTRET) // A CSR write overrides its own retirement increment.
  emit(0x13)
  read(CSR_INSTRET)  // Includes both the preceding CSR read and the NOP.
  emit(0x00100293)
  emit(0x00502023) // sw t0, 0(zero)
  emit(0x00002383) // lw t2, 0(zero)
  emit(0x00738433) // add s0, t2, t2 (load-use bubbles)
  read(CSR_MINSTRET)
  read(CSR_INSTRET)

  // Carry from the low half must be visible to both high-half aliases.
  for (addr <- Seq(CSR_MINSTRETH, CSR_INSTRETH)) {
    modify(CSR_MINSTRETH, 0)
    modify(CSR_MINSTRET, -1)
    emit(0x13)
    read(addr)
  }

  // Read-modify-write operations return the ordered snapshot and replace,
  // rather than add to, the counter's implicit increment.
  modify(CSR_MINSTRET, 7, returnOld = true)
  read(CSR_INSTRET)
  modify(CSR_MINSTRET, 16, operation = 2, returnOld = true)
  read(CSR_MINSTRET)
  modify(CSR_MINSTRET, 8, operation = 3, returnOld = true)
  read(CSR_INSTRET)
  modify(CSR_MINSTRETH, 3, returnOld = true)
  read(CSR_MINSTRET)
  read(CSR_INSTRETH)

  val donePc = RESET_PC.litValue + 4 * words.size
  emit(0x00100f93)
  emit(0x0000006f)
  def writeRom(): String = {
    val dir = new File("build/csr-tests")
    dir.mkdirs()
    val file = new File(dir, "instret-read.hex")
    val out = new PrintWriter(file)
    try words.padTo(256, 0x13L).foreach(word => out.println(f"$word%08x"))
    finally out.close()
    file.getAbsolutePath
  }
}

class CoreInstretReadTester(c: CoreWrapper) extends PeekPokeTester(c) {
  val pending = scala.collection.mutable.Map.from(InstretReadProgram.expected)
  var done = false
  for (_ <- 0 until 1000 if !done) {
    step(1)
    if (peek(c.io.regWen) != 0) {
      val pc = peek(c.io.pc)
      pending.remove(pc).foreach(value => expect(c.io.regWdata, value))
      done = pc == InstretReadProgram.donePc
    }
  }
  assert(done, "Instruction-retired CSR program did not finish")
  assert(pending.isEmpty, s"Missing CSR readbacks: ${pending.keys.mkString(", ")}")
}

object CoreInstretReadTest extends App {
  val path = InstretReadProgram.writeRom()
  if (!TestDriver.execute(args, () => new CoreWrapper(path))(c => new CoreInstretReadTester(c))) sys.exit(1)
}
