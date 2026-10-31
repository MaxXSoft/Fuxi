package core.integration

import org.scalatest.funsuite.AnyFunSuite

import org.chipsalliance.cde.config.Parameters
import utils.DefaultTestConfig._
import chisel3._
import chisel3.util.experimental.BoringUtils
import sim.ROM
import utils.{CoreMemoryHarness, CoreTraceProgram, CoreTester, RetirementChecker, TestDriver}
import utils.InstEncoding._

// Two sites alternate calls to the same function. Its single return PC must
// predict different targets; nested x5 calls also exercise the alternate link.
private[core] object RasProgram extends CoreTraceProgram {
  def write(rd: Int, data: BigInt): Option[(Int, BigInt)] = Some(rd -> data)
  word(addi(2, 0, 128), write(2, 128))
  word(sw(0, 2, 0))
  word(addi(8, 0, 8), write(8, 8))
  word(addi(10, 0, 0), write(10, 0))
  val loopPc = pc
  // Place the loop once, then describe its dynamic retirement sequence.
  emit32(lw(9, 2, 0))
  val firstCall = pc
  emit32(jal(1, (0x302 - pc).toInt))
  val secondCall = pc
  emit32(jal(1, (0x302 - pc).toInt))
  val decrement = pc
  emit16(c_addi(8, -1))
  val branch = pc
  emit32(bne(8, 0, (loopPc - pc).toInt))
  for (iteration <- 0 until 8) {
    event(loopPc, write(9, 0))
    for ((call, index) <- Seq(firstCall, secondCall).zipWithIndex) {
      event(call, write(1, call + 4))
      event(0x302, write(10, iteration * 4 + index * 2 + 1))
      event(0x304, write(5, 0x308))
      event(0x342, write(10, iteration * 4 + index * 2 + 2))
      event(0x344)
      event(0x308)
    }
    event(decrement, write(8, 7 - iteration))
    event(branch)
  }
  finish()
  place16(0x302, c_addi(10, 1))
  place32(0x304, jal(5, 0x342 - 0x304))
  place16(0x308, c_jr(1))
  place16(0x342, c_addi(10, 1))
  place16(0x344, c_jr(5))
}

private[core] class CoreRasHarness(depth: Int)(implicit parameters: Parameters)
    extends CoreMemoryHarness(ROM.Words(RasProgram.words))(new configs.WithFetchQueueDepth(depth) ++ parameters) {
  val prediction = IO(new Bundle {
    val returnAccepted = Output(Bool())
    val pc = Output(UInt(32.W))
    val target = Output(UInt(32.W))
    val flush = Output(Bool())
    val rasValid = Output(Bool())
  })
  prediction.returnAccepted := BoringUtils.bore(core.fetch.bpu.predictReturn) &&
                               BoringUtils.bore(core.fetch.bpu.io.advance)
  prediction.pc := BoringUtils.bore(core.fetch.io.fetch.pc)
  prediction.target := BoringUtils.bore(core.fetch.io.fetch.target)
  prediction.flush := BoringUtils.bore(core.control.io.flushIf)
  prediction.rasValid := BoringUtils.bore(core.fetch.bpu.ras.io.valid)
}

private[core] class CoreRasTester(c: CoreRasHarness, depth: Int) extends CoreTester(c) {
  val retired = new RetirementChecker(RasProgram.retired.toVector)
  val outerTargets = scala.collection.mutable.Set.empty[BigInt]
  var innerPredictions = 0
  var flushes = 0
  var maxQueueCount = 0
  var waitCycles = 0
  var armed = true
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)

  runUntil(4000, s"Missing RAS execution events: ${retired.missing}") {
    if (peek(c.io.memoryRequest) == 0) armed = true
    else if (armed) { waitCycles = 16; armed = false }
    poke(c.io.memoryStall, waitCycles > 0)
    waitCycles = math.max(0, waitCycles - 1)
    maxQueueCount = math.max(maxQueueCount, peek(c.io.queueCount).toInt)
    expect(c.io.observation.trap, false)
    retired.checkCount(peek(c.io.observation.count))
    if (peek(c.prediction.returnAccepted) != 0) {
      val pc = peek(c.prediction.pc)
      val target = peek(c.prediction.target)
      if (pc == 0x308) outerTargets += target
      if (pc == 0x344 && target == 0x308) innerPredictions += 1
    }
    val flush = peek(c.prediction.flush) != 0
    if (flush) flushes += 1
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      retired.observe(actual)
      done = actual.pc == RasProgram.donePc
    }
    step(1)
    if (flush) expect(c.prediction.rasValid, false)
    done
  }
  retired.checkComplete()
  retired.checkCount(peek(c.io.observation.count))
  assert(outerTargets.contains(RasProgram.firstCall + 4) &&
         outerTargets.contains(RasProgram.secondCall + 4),
         s"Same return did not predict both caller continuations: $outerTargets")
  assert(innerPredictions > 0, "No nested x5 return prediction")
  assert(flushes > 0, "No cold-target/branch recovery exercised")
  assert(maxQueueCount == depth, "Memory stalls did not fill the IF/ID FIFO")
}

class CoreRasTest extends AnyFunSuite {
  test("execute return prediction and recovery scenarios") {
    for (depth <- Seq(1, 4)) {
      TestDriver.run(new CoreRasHarness(depth)) {
        c => new CoreRasTester(c, depth)
      }
    }
  }
}
