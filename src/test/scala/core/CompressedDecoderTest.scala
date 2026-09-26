package core

import utils.{PeekPokeTester, TestDriver}

class CompressedDecoderTester(c: CompressedDecoder) extends PeekPokeTester(c) {
  val nop = 0x13L
  val expected = Array.fill[Option[Long]](1 << 16)(None)
  var hints = 0

  def unsigned(value: Long): Long = value & 0xffffffffL
  def iType(opcode: Int, funct3: Int, rd: Int, rs1: Int, imm: Int): Long =
    unsigned(((imm & 0xfff).toLong << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode)
  def rType(funct7: Int, funct3: Int, rd: Int, rs1: Int, rs2: Int): Long =
    unsigned((funct7.toLong << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | 0x33)
  def store(rs2: Int, rs1: Int, offset: Int): Long =
    unsigned(((offset & 0xfe0).toLong << 20) | (rs2 << 20) | (rs1 << 15) |
      (2 << 12) | ((offset & 0x1f) << 7) | 0x23)
  def branch(funct3: Int, rs1: Int, offset: Int): Long =
    unsigned(((offset & 0x1000).toLong << 19) | ((offset & 0x7e0) << 20) |
      (rs1 << 15) | (funct3 << 12) | ((offset & 0x1e) << 7) |
      ((offset & 0x800) >> 4) | 0x63)
  def jump(rd: Int, offset: Int): Long =
    unsigned(((offset & 0x100000).toLong << 11) | ((offset & 0x7fe) << 20) |
      ((offset & 0x800) << 9) | (offset & 0xff000) | (rd << 7) | 0x6f)

  // Encode architectural operands into compressed formats, independently of
  // the RTL decoder's extraction and sign-extension expressions.
  def scatter(value: Int, fields: (Int, Int)*): Int =
    fields.foldLeft(0) { case (bits, (source, destination)) =>
      bits | (((value >>> source) & 1) << destination)
    }
  def ci(funct3: Int, rd: Int, imm: Int, quadrant: Int = 1): Int =
    (funct3 << 13) | (rd << 7) | ((imm & 0x20) << 7) | ((imm & 0x1f) << 2) | quadrant
  def cj(funct3: Int, offset: Int): Int =
    (funct3 << 13) | 1 | scatter(offset, 11 -> 12, 4 -> 11, 9 -> 10,
      8 -> 9, 10 -> 8, 6 -> 7, 7 -> 6, 3 -> 5, 2 -> 4, 1 -> 3, 5 -> 2)
  def cb(funct3: Int, rs1: Int, offset: Int): Int =
    (funct3 << 13) | ((rs1 - 8) << 7) | 1 |
      scatter(offset, 8 -> 12, 4 -> 11, 3 -> 10, 7 -> 6, 6 -> 5, 2 -> 4, 1 -> 3, 5 -> 2)
  def word(funct3: Int, reg: Int, rs1: Int, offset: Int): Int =
    (funct3 << 13) | ((rs1 - 8) << 7) | ((reg - 8) << 2) |
      scatter(offset, 5 -> 12, 4 -> 11, 3 -> 10, 2 -> 6, 6 -> 5)

  def add(encoding: Int, instruction: Long, hint: Boolean = false): Unit = {
    assert((encoding & 3) != 3 && encoding >= 0 && encoding < expected.length)
    assert(expected(encoding).isEmpty, f"overlapping oracle encoding 0x$encoding%04x")
    expected(encoding) = Some(if (hint) nop else instruction)
    if (hint) hints += 1
  }

  for (rd <- 8 until 16; imm <- 4 to 1020 by 4) {
    val encoding = ((rd - 8) << 2) | scatter(imm, 5 -> 12, 4 -> 11,
      9 -> 10, 8 -> 9, 7 -> 8, 6 -> 7, 2 -> 6, 3 -> 5)
    add(encoding, iType(0x13, 0, rd, 2, imm))
  }
  for (reg <- 8 until 16; rs1 <- 8 until 16; offset <- 0 to 124 by 4) {
    add(word(2, reg, rs1, offset), iType(0x03, 2, reg, rs1, offset))
    add(word(6, reg, rs1, offset), store(reg, rs1, offset))
  }
  for (rd <- 0 until 32; imm <- -32 to 31) {
    add(ci(0, rd, imm), iType(0x13, 0, rd, rd, imm),
      hint = (rd == 0 || imm == 0) && (rd != 0 || imm != 0))
    add(ci(2, rd, imm), iType(0x13, 0, rd, 0, imm), hint = rd == 0)
    if (rd != 2 && imm != 0) {
      add(ci(3, rd, imm), unsigned((imm.toLong << 12) | (rd << 7) | 0x37), hint = rd == 0)
    }
  }
  for (imm <- -512 to 496 by 16 if imm != 0) {
    val encoding = 0x6101 | scatter(imm, 9 -> 12, 4 -> 6, 6 -> 5, 8 -> 4, 7 -> 3, 5 -> 2)
    add(encoding, iType(0x13, 0, 2, 2, imm))
  }
  for (offset <- -2048 to 2046 by 2) {
    add(cj(1, offset), jump(1, offset))
    add(cj(5, offset), jump(0, offset))
  }
  for (rs1 <- 8 until 16; offset <- -256 to 254 by 2) {
    add(cb(6, rs1, offset), branch(0, rs1, offset))
    add(cb(7, rs1, offset), branch(1, rs1, offset))
  }
  for (rd <- 8 until 16; shamt <- 0 until 32) {
    val regBits = (rd - 8) << 7
    add(0x8001 | regBits | (shamt << 2), iType(0x13, 5, rd, rd, shamt), hint = shamt == 0)
    add(0x8401 | regBits | (shamt << 2), iType(0x13, 5, rd, rd, 0x400 | shamt), hint = shamt == 0)
  }
  for (rd <- 8 until 16; imm <- -32 to 31) {
    val encoding = 0x8801 | ((rd - 8) << 7) | ((imm & 0x20) << 7) | ((imm & 0x1f) << 2)
    add(encoding, iType(0x13, 7, rd, rd, imm))
  }
  for (rd <- 8 until 16; rs2 <- 8 until 16; op <- 0 until 4) {
    val (funct7, funct3) = Seq((0x20, 0), (0, 4), (0, 6), (0, 7))(op)
    val encoding = 0x8c01 | ((rd - 8) << 7) | (op << 5) | ((rs2 - 8) << 2)
    add(encoding, rType(funct7, funct3, rd, rd, rs2))
  }
  for (rd <- 0 until 32; shamt <- 0 until 32) {
    add(ci(0, rd, shamt, quadrant = 2), iType(0x13, 1, rd, rd, shamt),
      hint = rd == 0 || shamt == 0)
  }
  for (rd <- 1 until 32; offset <- 0 to 252 by 4) {
    val encoding = 0x4002 | (rd << 7) |
      scatter(offset, 5 -> 12, 4 -> 6, 3 -> 5, 2 -> 4, 7 -> 3, 6 -> 2)
    add(encoding, iType(0x03, 2, rd, 2, offset))
  }
  for (rs2 <- 0 until 32; offset <- 0 to 252 by 4) {
    val encoding = 0xc002 | (rs2 << 2) |
      scatter(offset, 5 -> 12, 4 -> 11, 3 -> 10, 2 -> 9, 7 -> 8, 6 -> 7)
    add(encoding, store(rs2, 2, offset))
  }
  for (rd <- 1 until 32) {
    add(0x8002 | (rd << 7), iType(0x67, 0, 0, rd, 0))
    add(0x9002 | (rd << 7), iType(0x67, 0, 1, rd, 0))
  }
  add(0x9002, 0x00100073L)
  for (rd <- 0 until 32; rs2 <- 1 until 32) {
    add(0x8002 | (rd << 7) | (rs2 << 2), rType(0, 0, rd, 0, rs2), hint = rd == 0)
    add(0x9002 | (rd << 7) | (rs2 << 2), rType(0, 0, rd, rd, rs2), hint = rd == 0)
  }

  assert(expected.count(_.isDefined) == 28823)
  assert(hints == 362)

  // Literal encodings checked with LLVM MC also guard against mistakes in
  // the operand encoders used to build the exhaustive reference table.
  for ((encoding, instruction) <- Seq(
    0x0040 -> 0x00410413L, // c.addi4spn s0, sp, 4
    0x1ffc -> 0x3fc10793L, // c.addi4spn a5, sp, 1020
    0x5fe0 -> 0x07c7a403L, // c.lw s0, 124(a5)
    0xdc7c -> 0x06f42e23L, // c.sw a5, 124(s0)
    0x3001 -> 0x801ff0efL, // c.jal -2048
    0xaffd -> 0x7fe0006fL, // c.j 2046
    0xd001 -> 0xf00400e3L, // c.beqz s0, -256
    0xeffd -> 0x0e079f63L, // c.bnez a5, 254
    0x717d -> 0xff010113L, // c.addi16sp sp, -16
    0x617d -> 0x1f010113L, // c.addi16sp sp, 496
    0x7101 -> 0xe0010113L, // c.addi16sp sp, -512
    0x50fe -> 0x0fc12083L, // c.lwsp ra, 252(sp)
    0xdffe -> 0x0ff12e23L, // c.swsp t6, 252(sp)
    0x557d -> 0xfff00513L, // c.li a0, -1
    0x6505 -> 0x00001537L, // c.lui a0, 1
    0x8082 -> 0x00008067L, // c.jr ra
    0x9502 -> 0x000500e7L, // c.jalr a0
    0x9002 -> 0x00100073L, // c.ebreak
    0x0506 -> 0x00151513L, // c.slli a0, 1
    0x83fd -> 0x01f7d793L, // c.srli a5, 31
    0x847d -> 0x41f45413L, // c.srai s0, 31
    0x9b81 -> 0xfe07f793L, // c.andi a5, -32
    0x8c1d -> 0x40f40433L, // c.sub s0, a5
    0x8fa1 -> 0x0087c7b3L, // c.xor a5, s0
    0x8c5d -> 0x00f46433L, // c.or s0, a5
    0x8fe1 -> 0x0087f7b3L, // c.and a5, s0
    0x852e -> 0x00b00533L, // c.mv a0, a1
    0x952e -> 0x00b50533L, // c.add a0, a1
  )) assert(expected(encoding).contains(instruction), f"oracle mismatch at 0x$encoding%04x")

  for (encoding <- expected.indices) {
    val input = (((encoding * 0x9e37L ^ 0x5aa5L) & 0xffffL) << 16) | encoding
    val compressed = (encoding & 3) != 3
    val illegal = compressed && expected(encoding).isEmpty
    val expanded = if (compressed) expected(encoding).getOrElse(0L) else input
    poke(c.io.inst, BigInt(input))
    assert(peek(c.io.isCompressed) == (if (compressed) 1 else 0), f"length at 0x$encoding%04x")
    assert(peek(c.io.illegal) == (if (illegal) 1 else 0), f"legality at 0x$encoding%04x")
    assert(peek(c.io.expanded) == BigInt(expanded),
      f"expansion at 0x$encoding%04x: expected 0x$expanded%08x")
  }
  for (instruction <- Seq(0x00000013L, 0x00100073L, 0x80000537L, 0xffffffffL)) {
    poke(c.io.inst, BigInt(instruction))
    expect(c.io.isCompressed, false)
    expect(c.io.illegal, false)
    expect(c.io.expanded, BigInt(instruction))
  }
  println("CompressedDecoder: all 65536 encodings checked (28823 legal C, 362 HINTs).")
}

object CompressedDecoderTest extends App {
  if (!TestDriver.execute(args, () => new CompressedDecoder) {
    c => new CompressedDecoderTester(c)
  }) sys.exit(1)
}
