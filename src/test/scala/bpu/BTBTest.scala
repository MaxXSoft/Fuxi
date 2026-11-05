package bpu

import configs.{DefaultConfig, WithBtbEntries}
import org.scalatest.funsuite.AnyFunSuite
import utils.{PeekPokeTester, TestDriver}

class BtbMemoryTester(c: BTB, entries: Int) extends PeekPokeTester(c) {
  case class Line(pc: BigInt, jump: Boolean, target: BigInt)
  val lines = Array.fill[Option[Line]](entries)(None)
  val random = new scala.util.Random(0x425442 + entries)
  val addressMask = (BigInt(1) << 32) - 2
  def index(pc: BigInt): Int = ((pc >> 1) & (entries - 1)).toInt
  def check(pc: BigInt): Unit = {
    poke(c.io.lookupPc, pc)
    val resident = lines(index(pc))
    val hit = resident.exists(_.pc == (pc & addressMask))
    expect(c.io.lookupBranch, hit)
    expect(c.io.lookupJump, hit && resident.get.jump)
    expect(c.io.lookupTarget, if (hit) resident.get.target else BigInt(0))
    // Candidate remains the resident target on a tag miss. Never inspect an
    // unwritten memory entry: its contents are deliberately unspecified.
    resident.foreach(line => expect(c.io.lookupTargetCandidate, line.target))
  }
  def update(pc: BigInt, jump: Boolean, target: BigInt, enable: Boolean): Unit = {
    poke(c.io.branch, enable)
    poke(c.io.jump, jump)
    poke(c.io.pc, pc)
    poke(c.io.target, target)
    check(pc) // old value before the write edge, including same-address reads
    step(1)
    if (enable) lines(index(pc)) = Some(Line(pc & addressMask, jump, target & addressMask))
    check(pc) // new value is visible immediately after the write edge
  }

  poke(c.io.branch, false)
  poke(c.io.jump, false)
  poke(c.io.pc, 0)
  poke(c.io.target, 0)
  for (i <- 0 until entries) check(BigInt(i * 2))
  for (i <- 0 until entries) update(BigInt(i * 2), true, BigInt(0x1000 + i * 2), true)
  for (i <- 0 until entries) {
    val oldPc = BigInt(i * 2)
    val alias = oldPc + entries * 2
    update(alias, false, BigInt(0x2001 + i * 2), false)
    check(oldPc)
    update(alias, false, BigInt(0x2001 + i * 2), true)
    check(oldPc) // same index, different tag must miss
  }
  for (_ <- 0 until 512) {
    val pc = BigInt(random.nextInt(entries * 8) * 2)
    val target = BigInt(random.nextLong() & 0xffffffffL)
    update(pc, random.nextBoolean(), target, random.nextInt(4) != 0)
    check(BigInt(random.nextInt(entries * 8) * 2))
  }
}

class BTBTest extends AnyFunSuite {
  test("preserve asynchronous lookup and atomic line replacement across BTB sizes") {
    for (entries <- Seq(2, 16, 64)) {
      val p = new WithBtbEntries(entries) ++ new DefaultConfig
      TestDriver.run(new BTB()(p)) { c => new BtbMemoryTester(c, entries) }
    }
  }
}
