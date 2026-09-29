package utils

// Fixed vectors are independent of the encoder implementation and RTL oracle.
object InstEncodingTest extends App {
  import InstEncoding._
  val vectors: Seq[(Long, Long)] = Seq(
    addi(10, 0, 42) -> 0x02a00513L,
    addi(5, 5, -2) -> 0xffe28293L,
    slti(5, 6, -3) -> 0xffd32293L,
    slli(11, 11, 31) -> 0x01f59593L,
    add(11, 12, 11) -> 0x00b605b3L,
    mul(12, 10, 11) -> 0x02b50633L,
    div(29, 7, 28) -> 0x03c3ceb3L,
    lui(5, 0x80000) -> 0x800002b7L,
    auipc(1, 0xfffff) -> 0xfffff097L,
    lb(10, 8, -9) -> 0xff740503L,
    lw(7, 1, 0) -> 0x0000a383L,
    sw(1, 2, 12) -> 0x00112623L,
    sw(5, 2, -4) -> 0xfe512e23L,
    beq(29, 29, 8) -> 0x01de8463L,
    bne(1, 0, 8) -> 0x00009463L,
    blt(11, 10, 40) -> 0x02a5c463L,
    bne(1, 0, -4) -> 0xfe009ee3L,
    jal(8, 353190) -> 0x3a65646fL,
    jal(0, -4) -> 0xffdff06fL,
    jalr(1, 1, -1228) -> 0xb34080e7L,
    csrrw(2, 0x340, 2) -> 0x34011173L,
    csrrs(6, 0xb02, 5) -> 0xb022a373L,
    csrrc(6, 0xb02, 5) -> 0xb022b373L,
    csrrsi(0, 0x300, 8) -> 0x30046073L,
    csrr(10, 0x341) -> 0x34102573L,
    csrw(0x341, 10) -> 0x34151073L,
    lr_w(4, 1) -> 0x1000a22fL,
    sc_w(4, 3, 1) -> 0x1830a22fL,
    amoadd_w(4, 3, 1) -> 0x0030a22fL,
    nop() -> 0x00000013L,
    fence_i() -> 0x0000100fL,
    sfence_vma() -> 0x12000073L,
    sfence_vma(1, 2) -> 0x12208073L,
    ecall() -> 0x00000073L,
    mret() -> 0x30200073L,
    sret() -> 0x10200073L,
    c_nop().toLong -> 0x0001L,
    c_addi(0, 1).toLong -> 0x0005L,
    c_addi(10, 0).toLong -> 0x0501L,
    c_addi(10, -1).toLong -> 0x157dL,
    c_li(0, 1).toLong -> 0x4005L,
    c_li(10, -1).toLong -> 0x557dL,
    c_mv(0, 1).toLong -> 0x8006L,
    c_add(0, 2).toLong -> 0x900aL,
    c_slli(0, 1).toLong -> 0x0006L,
    c_srli(8, 0).toLong -> 0x8001L,
    c_srli(15, 31).toLong -> 0x83fdL,
    c_j(0).toLong -> 0xa001L,
    c_j(2046).toLong -> 0xaffdL,
    c_jal(-2048).toLong -> 0x3001L,
    c_beqz(8, 6).toLong -> 0xc019L,
    c_beqz(8, -256).toLong -> 0xd001L,
    c_bnez(15, 254).toLong -> 0xeffdL,
    c_add(10, 11).toLong -> 0x952eL,
    c_mv(13, 1).toLong -> 0x8686L,
    c_jr(1).toLong -> 0x8082L,
    c_jalr(5).toLong -> 0x9282L,
    c_ebreak().toLong -> 0x9002L,
    c_lwsp(1, 252).toLong -> 0x50feL,
    c_swsp(31, 252).toLong -> 0xdffeL,
  )
  for ((actual, expected) <- vectors)
    assert(actual == expected, f"Encoding 0x$actual%08x; expected 0x$expected%08x")

  def rejects(encode: => Any): Unit = {
    var rejected = false
    try encode catch { case _: IllegalArgumentException => rejected = true }
    assert(rejected, "Invalid operands must not silently truncate or change the instruction")
  }
  rejects(addi(32, 0, 0)); rejects(addi(1, -1, 0)); rejects(addi(1, 0, 2048))
  rejects(addi(1, 0, -2049)); rejects(slli(1, 1, 32)); rejects(lui(1, 1 << 20))
  rejects(beq(0, 0, 3)); rejects(jal(0, 1 << 20)); rejects(csrw(0x1000, 1))
  rejects(csrrsi(0, 0x300, 32)); rejects(sc_w(1, 32, 0))
  rejects(c_addi(1, 32)); rejects(c_j(1)); rejects(c_j(-2050))
  rejects(c_beqz(7, 0)); rejects(c_bnez(8, 256)); rejects(c_lwsp(0, 0))
  rejects(c_lwsp(1, 2)); rejects(c_swsp(1, 256)); rejects(c_jr(0))
  rejects(c_mv(1, 0)); rejects(c_add(1, 0)); rejects(c_slli(1, 32)); rejects(c_srli(7, 0))
  println(s"InstEncoding: ${vectors.size} fixed vectors and operand validation passed.")
}
