package core

// Operand encoders for integration-test programs; decoder oracles remain independent.
object InstEncoding {
  def addi(rd: Int, rs1: Int, imm: Int): Long =
    ((imm & 0xfff).toLong << 20) | (rs1 << 15) | (rd << 7) | 0x13
  def csr(addr: Int, rs1: Int, funct3: Int = 1, rd: Int = 0): Long =
    (addr.toLong << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | 0x73
  def sw(rs2: Int, rs1: Int, imm: Int): Long =
    ((imm & 0xfe0).toLong << 20) | (rs2 << 20) | (rs1 << 15) |
      (2 << 12) | ((imm & 31) << 7) | 0x23
  def jal(rd: Int, offset: Int): Long =
    ((offset & 0x100000).toLong << 11) | ((offset & 0x7fe).toLong << 20) |
      ((offset & 0x800) << 9) | (offset & 0xff000) | (rd << 7) | 0x6f
  def ci(funct3: Int, rd: Int, imm: Int): Int =
    (funct3 << 13) | ((imm & 32) << 7) | (rd << 7) | ((imm & 31) << 2) | 1
  def cj(funct3: Int, offset: Int): Int = {
    val fields = Seq(11 -> 12, 4 -> 11, 9 -> 10, 8 -> 9, 10 -> 8,
      6 -> 7, 7 -> 6, 3 -> 5, 2 -> 4, 1 -> 3, 5 -> 2)
    (funct3 << 13) | 1 | fields.map { case (from, to) => ((offset >>> from) & 1) << to }.sum
  }
  def c_j(offset: Int): Int = cj(5, offset)
  def lwsp(rd: Int, offset: Int): Int =
    0x4002 | (rd << 7) | ((offset & 32) << 7) | ((offset & 28) << 2) | ((offset & 192) >> 4)
  def lr(rd: Int, base: Int): Long =
    (2L << 27) | (base << 15) | (2 << 12) | (rd << 7) | 0x2f
  def sc(rd: Int, rs: Int, base: Int): Long =
    (3L << 27) | (rs << 20) | (base << 15) | (2 << 12) | (rd << 7) | 0x2f
}
