package core

// Operand encoders for test stimuli. Decoder oracles and fixed encoding vectors
// remain independent. Raw image writes can still construct reserved encodings.
object InstEncoding {
  private def reg(value: Int): Unit = require(value >= 0 && value < 32, "Invalid register")
  private def compactReg(value: Int): Unit = require(value >= 8 && value < 16, "Expected x8 through x15")
  private def signed(value: Int, bits: Int): Unit =
    require(value >= -(1 << (bits - 1)) && value < (1 << (bits - 1)), "Immediate out of range")
  private def unsigned(value: Int, bits: Int): Unit =
    require(value >= 0 && value < (1 << bits), "Immediate out of range")
  private def offset(value: Int, bits: Int): Unit = { signed(value, bits); require((value & 1) == 0) }
  private def stackOffset(value: Int): Unit = { unsigned(value, 8); require((value & 3) == 0) }
  private def scatter(value: Int, fields: (Int, Int)*): Int =
    fields.map { case (from, to) => ((value >>> from) & 1) << to }.sum

  private def iType(opcode: Int, funct3: Int, rd: Int, rs1: Int, imm: Int): Long = {
    reg(rd); reg(rs1); signed(imm, 12)
    ((imm & 0xfff).toLong << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode
  }
  private def rType(funct7: Int, funct3: Int, rd: Int, rs1: Int, rs2: Int): Long = {
    reg(rd); reg(rs1); reg(rs2)
    (funct7.toLong << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | 0x33
  }
  private def uType(opcode: Int, rd: Int, imm20: Int): Long = {
    reg(rd); unsigned(imm20, 20)
    (imm20.toLong << 12) | (rd << 7) | opcode
  }
  private def branch(funct3: Int, rs1: Int, rs2: Int, displacement: Int): Long = {
    reg(rs1); reg(rs2); offset(displacement, 13)
    ((displacement & 0x1000).toLong << 19) | ((displacement & 0x7e0).toLong << 20) |
      (rs2 << 20) | (rs1 << 15) | (funct3 << 12) |
      ((displacement & 0x1e) << 7) | ((displacement & 0x800) >> 4) | 0x63
  }
  private def csr(funct3: Int, rd: Int, address: Int, source: Int): Long = {
    reg(rd); unsigned(address, 12); unsigned(source, 5)
    (address.toLong << 20) | (source << 15) | (funct3 << 12) | (rd << 7) | 0x73
  }
  private def atomic(funct5: Int, rd: Int, rs2: Int, base: Int): Long = {
    reg(rd); reg(rs2); reg(base)
    (funct5.toLong << 27) | (rs2 << 20) | (base << 15) | (2 << 12) | (rd << 7) | 0x2f
  }

  def addi(rd: Int, rs1: Int, imm: Int): Long = iType(0x13, 0, rd, rs1, imm)
  def slti(rd: Int, rs1: Int, imm: Int): Long = iType(0x13, 2, rd, rs1, imm)
  def slli(rd: Int, rs1: Int, shamt: Int): Long = { unsigned(shamt, 5); iType(0x13, 1, rd, rs1, shamt) }
  def add(rd: Int, rs1: Int, rs2: Int): Long = rType(0, 0, rd, rs1, rs2)
  def mul(rd: Int, rs1: Int, rs2: Int): Long = rType(1, 0, rd, rs1, rs2)
  def div(rd: Int, rs1: Int, rs2: Int): Long = rType(1, 4, rd, rs1, rs2)
  // U-type immediates are the unsigned 20-bit field, not the shifted value.
  def lui(rd: Int, imm20: Int): Long = uType(0x37, rd, imm20)
  def auipc(rd: Int, imm20: Int): Long = uType(0x17, rd, imm20)
  def lb(rd: Int, base: Int, imm: Int): Long = iType(0x03, 0, rd, base, imm)
  def lw(rd: Int, base: Int, imm: Int): Long = iType(0x03, 2, rd, base, imm)
  def sw(rs2: Int, base: Int, imm: Int): Long = {
    reg(rs2); reg(base); signed(imm, 12)
    ((imm & 0xfe0).toLong << 20) | (rs2 << 20) | (base << 15) | (2 << 12) | ((imm & 31) << 7) | 0x23
  }
  def beq(rs1: Int, rs2: Int, displacement: Int): Long = branch(0, rs1, rs2, displacement)
  def bne(rs1: Int, rs2: Int, displacement: Int): Long = branch(1, rs1, rs2, displacement)
  def blt(rs1: Int, rs2: Int, displacement: Int): Long = branch(4, rs1, rs2, displacement)
  def jal(rd: Int, displacement: Int): Long = {
    reg(rd); offset(displacement, 21)
    ((displacement & 0x100000).toLong << 11) | ((displacement & 0x7fe).toLong << 20) |
      ((displacement & 0x800) << 9) | (displacement & 0xff000) | (rd << 7) | 0x6f
  }
  def jalr(rd: Int, rs1: Int, imm: Int): Long = iType(0x67, 0, rd, rs1, imm)
  def csrrw(rd: Int, address: Int, rs1: Int): Long = csr(1, rd, address, rs1)
  def csrrs(rd: Int, address: Int, rs1: Int): Long = csr(2, rd, address, rs1)
  def csrrc(rd: Int, address: Int, rs1: Int): Long = csr(3, rd, address, rs1)
  def csrrsi(rd: Int, address: Int, imm: Int): Long = csr(6, rd, address, imm)
  def csrr(rd: Int, address: Int): Long = csrrs(rd, address, 0)
  def csrw(address: Int, rs1: Int): Long = csrrw(0, address, rs1)
  def lr_w(rd: Int, base: Int): Long = atomic(2, rd, 0, base)
  def sc_w(rd: Int, rs2: Int, base: Int): Long = atomic(3, rd, rs2, base)
  def amoadd_w(rd: Int, rs2: Int, base: Int): Long = atomic(0, rd, rs2, base)
  def nop(): Long = addi(0, 0, 0)
  def fence_i(): Long = 0x0000100fL
  def sfence_vma(rs1: Int = 0, rs2: Int = 0): Long = {
    reg(rs1); reg(rs2)
    0x12000073L | (rs1 << 15) | (rs2 << 20)
  }
  def ecall(): Long = 0x00000073L
  def mret(): Long = 0x30200073L
  def sret(): Long = 0x10200073L

  private def ci(funct3: Int, rd: Int, imm: Int): Int = {
    reg(rd); signed(imm, 6)
    (funct3 << 13) | ((imm & 32) << 7) | (rd << 7) | ((imm & 31) << 2) | 1
  }
  private def cj(funct3: Int, displacement: Int): Int = {
    offset(displacement, 12)
    (funct3 << 13) | 1 | scatter(displacement, 11 -> 12, 4 -> 11, 9 -> 10, 8 -> 9, 10 -> 8,
      6 -> 7, 7 -> 6, 3 -> 5, 2 -> 4, 1 -> 3, 5 -> 2)
  }
  private def cb(funct3: Int, rs1: Int, displacement: Int): Int = {
    compactReg(rs1); offset(displacement, 9)
    (funct3 << 13) | ((rs1 - 8) << 7) | 1 |
      scatter(displacement, 8 -> 12, 4 -> 11, 3 -> 10, 7 -> 6, 6 -> 5, 2 -> 4, 1 -> 3, 5 -> 2)
  }
  // Preserve architectural HINT encodings (including x0 and zero immediates).
  def c_addi(rd: Int, imm: Int): Int = ci(0, rd, imm)
  def c_li(rd: Int, imm: Int): Int = ci(2, rd, imm)
  def c_nop(): Int = c_addi(0, 0)
  def c_j(displacement: Int): Int = cj(5, displacement)
  def c_jal(displacement: Int): Int = cj(1, displacement)
  def c_beqz(rs1: Int, displacement: Int): Int = cb(6, rs1, displacement)
  def c_bnez(rs1: Int, displacement: Int): Int = cb(7, rs1, displacement)
  def c_mv(rd: Int, rs2: Int): Int = {
    reg(rd); reg(rs2); require(rs2 != 0)
    0x8002 | (rd << 7) | (rs2 << 2)
  }
  def c_add(rd: Int, rs2: Int): Int = c_mv(rd, rs2) | 0x1000
  def c_jr(rs1: Int): Int = { reg(rs1); require(rs1 != 0); 0x8002 | (rs1 << 7) }
  def c_jalr(rs1: Int): Int = c_jr(rs1) | 0x1000
  def c_ebreak(): Int = 0x9002
  def c_lwsp(rd: Int, displacement: Int): Int = {
    reg(rd); require(rd != 0); stackOffset(displacement)
    0x4002 | (rd << 7) | scatter(displacement, 5 -> 12, 4 -> 6, 3 -> 5, 2 -> 4, 7 -> 3, 6 -> 2)
  }
  def c_swsp(rs2: Int, displacement: Int): Int = {
    reg(rs2); stackOffset(displacement)
    0xc002 | (rs2 << 2) | scatter(displacement, 5 -> 12, 4 -> 11, 3 -> 10, 2 -> 9, 7 -> 8, 6 -> 7)
  }
  def c_slli(rd: Int, shamt: Int): Int = {
    reg(rd); unsigned(shamt, 5)
    0x0002 | (rd << 7) | (shamt << 2)
  }
  def c_srli(rd: Int, shamt: Int): Int = {
    compactReg(rd); unsigned(shamt, 5)
    0x8001 | ((rd - 8) << 7) | (shamt << 2)
  }
}
