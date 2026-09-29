package core

import core.InstEncoding._
import chisel3._
import consts.CSR._
import utils.TestDriver

object InstretReadProgram extends CoreProgram {
  private val mask32 = (BigInt(1) << 32) - 1
  private var count = BigInt(0)

  override def emit16(half: Int): Unit = { super.emit16(half); count += 1 }
  override def emit32(word: Long): Unit = { super.emit32(word); count += 1 }
  def high(addr: UInt): Boolean = (addr.litValue & 0x80) != 0
  def value(addr: UInt): BigInt = if (high(addr)) count >> 32 else count & mask32
  def assign(addr: UInt, data: BigInt): Unit = {
    count = if (high(addr)) ((data & mask32) << 32) | (count & mask32)
            else (count & ~mask32) | (data & mask32)
  }
  def read(addr: UInt): Unit = {
    expectWriteback(6, value(addr))
    emit32(csrr(6, addr.litValue.toInt)) // csrr t1, addr
  }
  def modify(addr: UInt, data: Int, operation: Int = 1, returnOld: Boolean = false): Unit = {
    emit32(addi(5, 0, data)) // li t0, signed 12-bit data
    val old = value(addr)
    if (returnOld) expectWriteback(6, old)
    val rd = if (returnOld) 6 else 0
    val instruction = operation match {
      case 1 => csrrw(rd, addr.litValue.toInt, 5)
      case 2 => csrrs(rd, addr.litValue.toInt, 5)
      case 3 => csrrc(rd, addr.litValue.toInt, 5)
    }
    super.emit32(instruction)
    val operand = BigInt(data) & mask32
    assign(addr, if (operation == 1) operand
                 else if (operation == 2) old | operand else old & ~operand)
  }

  modify(CSR_MINSTRETH, 0)
  modify(CSR_MINSTRET, 0)
  read(CSR_MINSTRET) // A CSR write overrides its own retirement increment.
  emit32(nop())
  read(CSR_INSTRET)  // Includes both the preceding CSR read and the NOP.
  emit32(addi(5, 0, 1))
  emit32(sw(5, 0, 0)) // sw t0, 0(zero)
  emit32(lw(7, 0, 0)) // lw t2, 0(zero)
  emit32(add(8, 7, 7)) // add s0, t2, t2 (load-use bubbles)
  read(CSR_MINSTRET)
  read(CSR_INSTRET)

  // Carry from the low half must be visible to both high-half aliases.
  for (addr <- Seq(CSR_MINSTRETH, CSR_INSTRETH)) {
    modify(CSR_MINSTRETH, 0)
    modify(CSR_MINSTRET, -1)
    emit32(nop())
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

  val donePc = finish()
}

object CoreInstretReadTest extends App {
  if (!TestDriver.execute(args, () => new CoreWrapper(InstretReadProgram.words))(
    c => new CoreProgramTester(c, InstretReadProgram, InstretReadProgram.donePc, 1000))) sys.exit(1)
}
