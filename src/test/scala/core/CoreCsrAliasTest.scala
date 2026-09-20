package core

import chisel3._
import consts.CSR._
import consts.Parameters.RESET_PC
import java.io.{File, PrintWriter}
import utils.{PeekPokeTester, TestDriver}

object CsrAliasProgram {
  val words = scala.collection.mutable.ArrayBuffer.empty[Long]
  val expected = scala.collection.mutable.Map.empty[BigInt, BigInt]
  def emit(word: Long): Unit = words += word
  def write(addr: UInt, value: Int): Unit = {
    emit(((value & 0xfff).toLong << 20) | 0x293) // addi t0, zero, value
    emit((addr.litValue.toLong << 20) | 0x29073) // csrw addr, t0
  }
  def read(addr: UInt, value: Int): Unit = {
    expected(RESET_PC.litValue + 4 * words.size) = value
    emit((addr.litValue.toLong << 20) | 0x2373) // csrrs t1, addr, zero
  }
  // Check both directions of every writable supervisor alias.
  write(CSR_MSTATUS, 2); read(CSR_SSTATUS, 2)
  write(CSR_SSTATUS, 0); read(CSR_MSTATUS, 0)
  write(CSR_MIDELEG, 0x222)
  write(CSR_MIE, 0x222); read(CSR_SIE, 0x222)
  write(CSR_SIE, 0); read(CSR_MIE, 0)
  write(CSR_MIP, 2); read(CSR_SIP, 2)
  write(CSR_SIP, 0); read(CSR_MIP, 0)
  // SIE/SIP reads also depend on a preceding delegation write.
  write(CSR_MIE, 0x222)
  write(CSR_MIP, 0x222)
  write(CSR_MIDELEG, 0); read(CSR_SIE, 0)
  write(CSR_MIDELEG, 0x222); read(CSR_SIP, 0x222)
  // High-half aliases are stable over the short test, unlike cycle's low half.
  write(CSR_MCYCLEH, 2); read(CSR_CYCLEH, 2)
  write(CSR_MINSTRETH, 3); read(CSR_INSTRETH, 3)
  val donePc = RESET_PC.litValue + 4 * words.size
  emit(0x00100f93) // addi t6, zero, 1
  emit(0x0000006f) // j .

  def writeRom(): String = {
    val dir = new File("build/csr-tests")
    dir.mkdirs()
    val file = new File(dir, "alias.hex")
    val out = new PrintWriter(file)
    try words.padTo(256, 0x13L).foreach(word => out.println(f"$word%08x"))
    finally out.close()
    file.getAbsolutePath
  }
}

class CoreCsrAliasTester(c: CoreWrapper) extends PeekPokeTester(c) {
  val pending = scala.collection.mutable.Map.from(CsrAliasProgram.expected)
  var done = false
  for (_ <- 0 until 600 if !done) {
    step(1)
    if (peek(c.io.regWen) != 0) {
      val pc = peek(c.io.pc)
      pending.remove(pc).foreach(value => expect(c.io.regWdata, value))
      done = pc == CsrAliasProgram.donePc
    }
  }
  assert(done, "CSR alias program did not finish")
  assert(pending.isEmpty, s"Missing CSR readbacks: ${pending.keys.mkString(", ")}")
}

object CoreCsrAliasTest extends App {
  val path = CsrAliasProgram.writeRom()
  if (!TestDriver.execute(args, () => new CoreWrapper(path))(c => new CoreCsrAliasTester(c))) sys.exit(1)
}
