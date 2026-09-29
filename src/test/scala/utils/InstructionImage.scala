package utils

// Sparse little-endian bytes, independent of ROM size and virtual addressing.
// The fill pattern is aligned to absolute 32-bit words.
class InstructionImage(fillWord: BigInt = 0) {
  require(fillWord >= 0 && fillWord < (BigInt(1) << 32))
  private val bytes = scala.collection.mutable.Map.empty[BigInt, Int]
  def endAddress: Option[BigInt] = bytes.keys.maxOption.map(_ + 1)

  private def put(address: BigInt, value: BigInt, size: Int, replace: Boolean): Unit = {
    require(address >= 0 && (address & 1) == 0, "Instruction address must be halfword aligned")
    require(value >= 0 && value < (BigInt(1) << (size * 8)), "Value exceeds encoded width")
    // Validate the entire write before changing any byte.
    require(replace || (0 until size).forall(i => !bytes.contains(address + i)),
      s"Overlapping instruction at 0x${address.toString(16)}")
    for (i <- 0 until size) bytes(address + i) = ((value >> (8 * i)) & 255).toInt
  }

  def place16(address: BigInt, value: BigInt): Unit = put(address, value, 2, replace = false)
  def place32(address: BigInt, value: BigInt): Unit = put(address, value, 4, replace = false)
  def replace16(address: BigInt, value: BigInt): Unit = put(address, value, 2, replace = true)
  def replace32(address: BigInt, value: BigInt): Unit = put(address, value, 4, replace = true)

  def read32(address: BigInt): BigInt = {
    require(address >= 0 && (address & 3) == 0, "Read address must be word aligned")
    (0 until 4).foldLeft(BigInt(0)) { (word, i) =>
      val byte = bytes.getOrElse(address + i, ((fillWord >> (8 * i)) & 255).toInt)
      word | (BigInt(byte) << (8 * i))
    }
  }

  def words(base: BigInt, count: Int): Seq[BigInt] = {
    require(count >= 0)
    (0 until count).map(i => read32(base + 4 * i))
  }
}

object InstructionImage {
  val CompressedNops: BigInt = 0x00010001
}
