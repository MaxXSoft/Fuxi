package core.integration

import configs._
import org.chipsalliance.cde.config.Parameters
import utils.{CoreProgram, CoreProgramTester, CoreWrapper, InstEncoding, TestDriver}

// Execute the same architectural checks with unrelated elaboration environments
// in one JVM. Program layout and ROM must use the selected reset vector too.
object CoreConfigTest extends App {
  val configurations = Seq[Parameters](
    new DefaultConfig, new SmallConfig, new ProfilingConfig,
    new WithResetVector(BigInt("80000000", 16)) ++
      new WithHartId(7) ++
      new WithFetchQueueDepth(1) ++
      new WithFetchBufferEntries(2) ++
      new WithGhrBits(2) ++
      new WithBtbEntries(2) ++
      new WithRasEntries(1) ++
      new WithMulLatency(0) ++
      new SmallConfig,
    new WithResetVector(0x1000) ++
      new WithHartId(BigInt("ffffffff", 16)) ++
      new WithFetchQueueDepth(3) ++
      new WithFetchBufferEntries(8) ++
      new WithGhrBits(3) ++
      new WithBtbEntries(8) ++
      new WithRasEntries(3) ++
      new WithMulLatency(3) ++
      new DefaultConfig
  )
  for (parameters <- configurations) {
    implicit val p: Parameters = parameters
    import InstEncoding._
    val program = new CoreProgram(utils.InstructionImage.CompressedNops)
    def write(rd: Int, expected: BigInt, instruction: Long): Unit = {
      program.expectWriteback(rd, expected)
      program.emit32(instruction)
    }
    write(10, p(PlatformParamsKey).hartId, csrr(10, 0xf14))
    // Check HPM presence through mcountinhibit's WARL mask.
    write(2, BigInt("ffffffff", 16), addi(2, 0, -1))
    program.emit32(csrw(0x320, 2))
    val inhibitMask = (((BigInt(1) << p(CoreParamsKey).hpmCounters) - 1) << 3) | 5
    write(11, inhibitMask, csrr(11, 0x320))
    program.emit16(c_nop()) // subsequent 32-bit instructions straddle words
    write(3, 7, addi(3, 0, 7))
    write(4, BigInt("fffffff9", 16), mul(4, 2, 3))
    write(5, BigInt("ffffffcf", 16), mul(5, 4, 3)) // dependent multiply
    write(6, 49, mul(6, 4, 4)) // immediately following multiply changes sign
    write(7, 12, addi(7, 0, 12))
    val loop = program.pc
    for (n <- 11 to 0 by -1) program.expectWriteback(7, n, 12 - n)
    program.emit32(addi(7, 7, -1))
    program.emit32(bne(7, 0, (loop - program.pc).toInt))
    val done = program.finish()
    if (!TestDriver.execute(args, () => new CoreWrapper(program.words)) {
      c => new CoreProgramTester(c, program, done, 2000)
    }) sys.exit(1)
  }
}
