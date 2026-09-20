package core

import chisel3._
import chisel3.util.experimental.BoringUtils
import consts.Parameters._
import java.io.{File, PrintWriter}
import utils.{PeekPokeTester, TestDriver}

object RetirementProgram {
  val words = Array.fill[Long](256)(0x13)
  val main = Seq[Long](
    0x30000293, // li t0, trap handler at 0x300
    0x30529073, // csrw mtvec, t0
    0x02a00293, // li t0, 42
    0x00502023, // sw t0, 0(zero)
    0x00002303, // lw t1, 0(zero)
    0x006303b3, // add t2, t1, t1 (load-use bubbles)
    0x00000013, // architectural nop
    0x00300e13, // li t3, 3
    0x03c3ceb3, // div t4, t2, t3 (multicycle stall)
    0x01de8463, // beq t4, t4, +8
    0x06300413, // squashed addi
    0x00000013, // nop at branch target
    0x0000100f, // fence.i
    0x12000073, // sfence.vma
    0x00000073, // ecall: does not retire
    0x00100f93, // completion marker
    0x0000006f, // j .
  )
  main.copyToArray(words)
  Seq[Long](
    0x341022f3, // csrr t0, mepc
    0x00428293, // addi t0, t0, 4
    0x34129073, // csrw mepc, t0
    0x30200073, // mret: retires
  ).copyToArray(words, 64)
  def pcs(fenceFault: Boolean): Seq[BigInt] = {
    val handler = Seq(64, 65, 66, 67)
    val fences = if (fenceFault) handler ++ handler else Seq(12, 13)
    ((0 to 9) ++ Seq(11) ++ fences ++ handler ++ Seq(15))
      .map(i => RESET_PC.litValue + i * 4)
  }
  val donePc = RESET_PC.litValue + 15 * 4

  def writeRom(): String = {
    val dir = new File("build/csr-tests")
    dir.mkdirs()
    val file = new File(dir, "retirement.hex")
    val out = new PrintWriter(file)
    try words.foreach(word => out.println(f"$word%08x"))
    finally out.close()
    file.getAbsolutePath
  }
}

class CoreRetirementSystem(path: String, fenceFault: Boolean) extends CoreWrapper(path) {
  core.io.cache.flushDataAccessFault := fenceFault.B
}

class CoreRetirementWrapper(path: String, fenceFault: Boolean) extends Module {
  val io = IO(new Bundle {
    val retired = Output(Bool())
    val pc = Output(UInt(ADDR_WIDTH.W))
    val count = Output(UInt(64.W))
  })
  val system = Module(new CoreRetirementSystem(path, fenceFault))
  io.retired := BoringUtils.bore(system.core.wb.io.csr.retired)
  io.pc := system.io.pc
  io.count := BoringUtils.bore(system.core.csrfile.minstret.data)
}

class CoreRetirementTester(c: CoreRetirementWrapper, fenceFault: Boolean) extends PeekPokeTester(c) {
  val expected = RetirementProgram.pcs(fenceFault)
  val retiredPcs = scala.collection.mutable.ArrayBuffer.empty[BigInt]
  var done = false
  for (_ <- 0 until 600 if !done) {
    expect(c.io.count, retiredPcs.size)
    if (peek(c.io.retired) != 0) {
      val pc = peek(c.io.pc)
      retiredPcs += pc
      done = pc == RetirementProgram.donePc
    }
    step(1)
  }
  assert(done, "Retirement program did not finish")
  expect(c.io.count, expected.size)
  assert(retiredPcs.toSeq == expected,
    s"Retired PCs: ${retiredPcs.map(_.toString(16)).mkString(", ")}; " +
    s"expected: ${expected.map(_.toString(16)).mkString(", ")}")
}

object CoreRetirementTest extends App {
  val path = RetirementProgram.writeRom()
  for (fenceFault <- Seq(false, true)) {
    if (!TestDriver.execute(args, () => new CoreRetirementWrapper(path, fenceFault))(
      c => new CoreRetirementTester(c, fenceFault))) sys.exit(1)
  }
}
