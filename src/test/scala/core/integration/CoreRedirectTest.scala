package core.integration

import org.scalatest.funsuite.AnyFunSuite

import chisel3._
import chisel3.util.experimental.BoringUtils
import org.chipsalliance.cde.config.Parameters
import sim.ROM
import utils.DefaultTestConfig._
import utils.InstEncoding._
import utils.{CoreMemoryHarness, CoreTester, CoreTraceProgram, ExpectedTrap,
              RetirementChecker, TestDriver, TrapChecker}

// The younger JAL requests a correction one cycle before ECALL takes its
// trap. Its saved target must lose to MTVEC and must never be replayed.
private object RedirectTrapProgram extends CoreTraceProgram {
  word(addi(2, 0, 128), Some(2 -> BigInt(128)))
  word(addi(5, 0, 85), Some(5 -> BigInt(85)))
  word(sw(5, 2, 0))
  word(addi(5, 0, 0x380), Some(5 -> BigInt(0x380)))
  word(csrw(0x305, 5))
  for (_ <- 0 until 4) word(nop())
  val faultPc = word(ecall(), retires = false)
  word(jal(0, (0x300 - pc).toInt), retires = false)
  word(sw(0, 2, 0), retires = false)
  place32(0x300, sw(0, 2, 0))
  place32(0x304, jal(0, 0))
  traps += ExpectedTrap(faultPc, 11, 0)
  seekAddress(0x380)
  word(csrr(6, 0x342), Some(6 -> BigInt(11)))
  word(csrr(7, 0x341), Some(7 -> faultPc))
  word(lw(8, 2, 0), Some(8 -> BigInt(85)))
  finish()
}

private class RedirectHarness(program: CoreTraceProgram, depth: Int)(implicit parameters: Parameters)
    extends CoreMemoryHarness(ROM.Words(program.words))(
      new configs.WithFetchQueueDepth(depth) ++ parameters) {
  val redirect = IO(new Bundle {
    val correction = Output(Bool())
    val correctionTarget = Output(UInt(32.W))
    val pending = Output(Bool())
    val globalFlush = Output(Bool())
    val fetchFlush = Output(Bool())
    val fetchTarget = Output(UInt(32.W))
    val decodeValid = Output(Bool())
    val queueValid = Output(Bool())
    val stallId = Output(Bool())
    val train = Output(Bool())
    val prediction = Output(Bool())
    val predictionPc = Output(UInt(32.W))
    val predictionTarget = Output(UInt(32.W))
    val requestActive = Output(Bool())
    val request = Output(Bool())
    val requestAddress = Output(UInt(32.W))
  })
  redirect.correction := BoringUtils.bore(core.decoder.io.flushIf)
  redirect.correctionTarget := BoringUtils.bore(core.decoder.io.flushPc)
  redirect.pending := BoringUtils.bore(core.control.io.decFlush)
  redirect.globalFlush := BoringUtils.bore(core.control.io.flush)
  redirect.fetchFlush := BoringUtils.bore(core.fetch.io.flush)
  redirect.fetchTarget := BoringUtils.bore(core.fetch.io.flushPc)
  redirect.decodeValid := BoringUtils.bore(core.decoder.io.fetch.valid)
  redirect.queueValid := BoringUtils.bore(core.ifid.io.deq.valid)
  redirect.stallId := BoringUtils.bore(core.control.io.stallId)
  redirect.train := BoringUtils.bore(core.decoder.io.branch.branch) ||
                    BoringUtils.bore(core.decoder.io.branch.jump)
  redirect.prediction := BoringUtils.bore(core.fetch.predictedRedirect)
  redirect.predictionPc := BoringUtils.bore(core.fetch.io.fetch.pc)
  redirect.predictionTarget := BoringUtils.bore(core.fetch.io.fetch.target)
  redirect.requestActive := BoringUtils.bore(core.fetch.requestActive)
  redirect.request := core.io.rom.en
  redirect.requestAddress := core.io.rom.addr
}

private class RedirectTester(c: RedirectHarness, program: CoreTraceProgram, trap: Boolean)
    extends CoreTester(c) {
  val retirements = new RetirementChecker(program.retired.toVector)
  val traps = new TrapChecker(program.traps.toVector)
  var previousCorrection = false
  var previousTarget = BigInt(0)
  var pendingCycles = 0
  var wrongPackets = 0
  var stalledCorrections = 0
  var supersededCorrections = 0
  var fastPredictions = 0
  var memoryWait = 0
  var memoryArmed = true
  val knownTargets = Map(RasProgram.firstCall -> BigInt(0x302),
                         RasProgram.secondCall -> BigInt(0x302),
                         BigInt(0x304) -> BigInt(0x34a))
  poke(c.io.memoryStall, false)
  poke(c.io.fenceStall, false)

  runUntil(6000, s"Missing redirect events: ${retirements.missing}; ${traps.missing}") {
    if (peek(c.io.memoryRequest) == 0) memoryArmed = true
    else if (memoryArmed && !trap) { memoryWait = 16; memoryArmed = false }
    poke(c.io.memoryStall, memoryWait > 0)
    memoryWait = math.max(0, memoryWait - 1)
    val correction = peek(c.redirect.correction) != 0
    val pending = peek(c.redirect.pending) != 0
    val globalFlush = peek(c.redirect.globalFlush) != 0
    if (globalFlush && peek(c.redirect.requestActive) == 0)
      expect(c.redirect.request, false)
    expect(c.redirect.pending, previousCorrection)
    if (pending) {
      pendingCycles += 1
      // A held FIFO packet must neither enter ID/EX nor retrain the BPU.
      expect(c.redirect.decodeValid, false)
      expect(c.redirect.train, false)
      expect(c.redirect.correction, false)
      expect(c.redirect.fetchFlush, true)
      if (peek(c.redirect.queueValid) != 0) wrongPackets += 1
      if (peek(c.redirect.stallId) != 0) stalledCorrections += 1
      if (globalFlush) {
        assert(trap, "Unexpected older redirect in the call/return program")
        expect(c.redirect.fetchTarget, 0x380)
        supersededCorrections += 1
      } else expect(c.redirect.fetchTarget, previousTarget)
    }
    if (correction) {
      expect(c.redirect.decodeValid, true)
      expect(c.redirect.stallId, false)
      expect(c.redirect.fetchFlush, false) // Not applied in its detection cycle.
      previousTarget = peek(c.redirect.correctionTarget)
    }
    previousCorrection = correction

    val predictionPc = peek(c.redirect.predictionPc)
    if (!trap && peek(c.redirect.prediction) != 0 &&
        peek(c.redirect.fetchFlush) == 0 && peek(c.redirect.requestActive) == 0 &&
        knownTargets.get(predictionPc).contains(peek(c.redirect.predictionTarget))) {
      expect(c.redirect.request, true)
      expect(c.redirect.requestAddress, knownTargets(predictionPc) & ~BigInt(3))
      fastPredictions += 1 // Correct predictions still issue in this cycle.
    }

    retirements.checkCount(peek(c.io.observation.count))
    checkTrap(c.io.observation, traps)
    var done = false
    if (peek(c.io.observation.retired) != 0) {
      val actual = sampleDebug(c.io.observation.debug)
      retirements.observe(actual)
      done = actual.pc == program.donePc
    }
    step(1)
    done
  }
  retirements.checkComplete()
  retirements.checkCount(peek(c.io.observation.count))
  traps.checkComplete()
  assert(pendingCycles > 0, "No registered corrections exercised")
  if (trap) assert(supersededCorrections == 1, "ECALL did not supersede the pending JAL")
  else {
    assert(wrongPackets > 0, "No younger packet was present during recovery")
    assert(stalledCorrections > 0, "No correction overlapped a backend stall")
    assert(fastPredictions > 0, "No correct prediction exercised same-cycle target issue")
  }
}

class CoreRedirectTest extends AnyFunSuite {
  test("register decode corrections while preserving predicted fetch and stalled execution") {
    for (depth <- Seq(1, 4)) {
      TestDriver.run(new RedirectHarness(RasProgram, depth)) { c =>
        new RedirectTester(c, RasProgram, trap = false)
      }
    }
  }
  test("give an older trap priority over a pending decode correction") {
    for (depth <- Seq(1, 4)) {
      TestDriver.run(new RedirectHarness(RedirectTrapProgram, depth)) { c =>
        new RedirectTester(c, RedirectTrapProgram, trap = true)
      }
    }
  }
}
