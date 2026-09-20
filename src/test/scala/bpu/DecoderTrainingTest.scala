package bpu

import chisel3._

import core.Decoder
import io.FetchIO
import consts.Parameters.GHR_WIDTH
import utils.{PeekPokeTester, TestDriver}

class DecoderTrainingHarness extends Module {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val stalled = Input(Bool())
    val inst = Input(UInt(32.W))
    val operand = Input(UInt(32.W))
    val branch = Output(Bool())
    val jump = Output(Bool())
    val taken = Output(Bool())
    val predictionIndex = Output(UInt(GHR_WIDTH.W))
    val predictionTarget = Output(UInt(32.W))
    val predictionTaken = Output(Bool())
  })
  val decoder = Module(new Decoder)
  val predictor = Module(new BranchPredictor)
  decoder.io.fetch := 0.U.asTypeOf(new FetchIO)
  decoder.io.fetch.valid := io.valid
  decoder.io.fetch.pc := 0x200.U
  decoder.io.inst := io.inst
  decoder.io.stallId := io.stalled
  decoder.io.read1.data := io.operand
  decoder.io.read2.data := 0.U
  predictor.io.branchInfo <> decoder.io.branch
  // These PC index bits are zero, so predIndex exposes the GHR through the
  // actual predictor lookup without relying on internal register names.
  predictor.io.lookupPc := 0x200.U
  io.branch := decoder.io.branch.branch
  io.jump := decoder.io.branch.jump
  io.taken := decoder.io.branch.taken
  io.predictionIndex := predictor.io.predIndex
  io.predictionTarget := predictor.io.predTarget
  io.predictionTaken := predictor.io.predTaken
}

class DecoderTrainingTester(c: DecoderTrainingHarness) extends PeekPokeTester(c) {
  poke(c.io.valid, true)
  poke(c.io.stalled, false)
  poke(c.io.inst, 0x00009463) // bne x1, x0, +8
  poke(c.io.operand, 0)
  step(GHR_WIDTH + 1) // known history and strongly not-taken PHT entry
  expect(c.io.predictionIndex, 0)
  expect(c.io.predictionTarget, 0x208)
  expect(c.io.predictionTaken, false)

  // A load-use or downstream stall keeps one branch in ID. Its operands may
  // change as forwarding resolves, but no prediction state may be trained.
  poke(c.io.stalled, true)
  for (operand <- Seq(1, 0, 1, 1)) {
    poke(c.io.operand, operand)
    expect(c.io.branch, false)
    expect(c.io.jump, false)
    expect(c.io.taken, false)
    step(1)
    expect(c.io.predictionIndex, 0)
    expect(c.io.predictionTarget, 0x208)
    expect(c.io.predictionTaken, false)
  }

  // The resolved branch trains once on the cycle that ID can advance.
  poke(c.io.stalled, false)
  expect(c.io.branch, true)
  expect(c.io.taken, true)
  step(1)
  expect(c.io.predictionIndex, 1)
  poke(c.io.valid, false)
  step(4)
  expect(c.io.branch, false)
  expect(c.io.predictionIndex, 1)

  // A stalled jump must not replace the prior target or shift the history.
  poke(c.io.inst, 0x0200006f) // jal x0, +32
  poke(c.io.valid, true)
  poke(c.io.stalled, true)
  for (_ <- 0 until 3) {
    expect(c.io.branch, false)
    expect(c.io.jump, false)
    expect(c.io.taken, false)
    step(1)
    expect(c.io.predictionIndex, 1)
    expect(c.io.predictionTarget, 0x208)
  }
  poke(c.io.stalled, false)
  expect(c.io.branch, true)
  expect(c.io.jump, true)
  step(1)
  expect(c.io.predictionIndex, 3)
  expect(c.io.predictionTarget, 0x220)
  expect(c.io.predictionTaken, true)
  poke(c.io.valid, false)
  step(3)
  expect(c.io.predictionIndex, 3)
  expect(c.io.predictionTarget, 0x220)
}

object DecoderTrainingTest extends App {
  if (!TestDriver.execute(args, () => new DecoderTrainingHarness) {
    c => new DecoderTrainingTester(c)
  }) sys.exit(1)
}
