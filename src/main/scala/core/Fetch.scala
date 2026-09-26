package core

import chisel3._
import chisel3.util._

import io._
import consts.Parameters._
import bpu.BranchPredictor

class FetchWord extends Bundle {
  val data = UInt(INST_WIDTH.W)
  val pageFault = Bool()
  val accessFault = Bool()
}

class Fetch extends Module {
  val io = IO(new Bundle {
    // pipeline control signals
    val flush     = Input(Bool())
    val contextFlush = Input(Bool())
    val stall     = Input(Bool())
    val flushPc   = Input(UInt(ADDR_WIDTH.W))
    // ROM interface
    val rom       = new SramIO(ADDR_WIDTH, INST_WIDTH)
    // branch information (from decoder)
    val branch    = Input(new BranchInfoIO)
    // to next stage
    val fetch     = Output(new FetchIO)
  })

  val pc = RegInit(RESET_PC)
  val faultBlocked = RegInit(false.B)
  val bpu = Module(new BranchPredictor)
  bpu.io.branchInfo <> io.branch
  bpu.io.lookupPc := pc

  // SRAM valid accepts a word now; its synchronous data arrives next cycle.
  // Keep an unaccepted request stable even when its instruction stream dies.
  val requestAddr = RegInit(RESET_PC)
  val requestActive = RegInit(false.B)
  val requestKilled = RegInit(false.B)
  val restartAddr = RegInit(RESET_PC)
  val pending = RegInit(false.B)
  val pendingKilled = RegInit(false.B)
  val pendingPageFault = Reg(Bool())
  val pendingAccessFault = Reg(Bool())

  val depth = 4
  val words = Reg(Vec(depth, new FetchWord))
  val head = RegInit(0.U(log2Ceil(depth).W))
  val tail = RegInit(0.U(log2Ceil(depth).W))
  val count = RegInit(0.U(log2Ceil(depth + 1).W))
  val nextHead = (head + 1.U)(log2Ceil(depth) - 1, 0)
  val responseValid = pending && !pendingKilled && !faultBlocked
  val response = Wire(new FetchWord)
  response.data := io.rom.rdata
  response.pageFault := pendingPageFault
  response.accessFault := pendingAccessFault

  // Bypass the returning word so the queue does not add a hit-path cycle.
  val first = Mux(count =/= 0.U, words(head), response)
  val second = Mux(count > 1.U, words(nextHead), response)
  val firstValid = count =/= 0.U || responseValid
  val secondValid = count > 1.U || (count === 1.U && responseValid)
  val firstFault = first.pageFault || first.accessFault
  val parcel = Mux(pc(1), first.data(31, 16), first.data(15, 0))
  val compressed = parcel(1, 0) =/= 3.U
  val split = pc(1) && !compressed && !firstFault
  val instructionValid = firstValid && (!split || secondValid) && !faultBlocked
  val pageFault = first.pageFault || (split && second.pageFault)
  val accessFault = first.accessFault || (split && second.accessFault)
  val instructionFault = pageFault || accessFault
  val instruction = Mux(compressed, Cat(0.U(16.W), parcel),
                      Mux(pc(1), Cat(second.data(15, 0), parcel), first.data))
  val sequentialPc = pc + Mux(compressed, 2.U, 4.U)
  val emit = instructionValid && !io.stall
  val predictedRedirect = emit && !instructionFault && bpu.io.predTaken &&
                            bpu.io.predTarget =/= sequentialPc
  val redirect = io.flush || predictedRedirect
  val target = Mux(io.flush, io.flushPc, bpu.io.predTarget)
  val alignedTarget = Cat(target(ADDR_WIDTH - 1, 2), 0.U(2.W))
  val stopOnFault = emit && instructionFault
  val cancel = redirect || stopOnFault

  io.fetch.valid := instructionValid
  io.fetch.pc := pc
  io.fetch.inst := instruction
  io.fetch.taken := bpu.io.predTaken
  io.fetch.target := bpu.io.predTarget
  io.fetch.predIndex := bpu.io.predIndex
  io.fetch.pageFault := pageFault
  io.fetch.accessFault := accessFault
  io.fetch.faultAddr := Mux(firstFault, pc, Mux(split, pc + 2.U, pc))

  when (io.flush) {
    pc := io.flushPc
    faultBlocked := false.B
  } .elsewhen (emit) {
    pc := Mux(predictedRedirect, bpu.io.predTarget, sequentialPc)
    when (instructionFault) { faultBlocked := true.B }
  }

  val consumeWord = emit && (pc(1) || !compressed || firstFault)
  val popStored = consumeWord && count =/= 0.U
  val pushStored = responseValid && !(count === 0.U && consumeWord)
  when (cancel) {
    head := 0.U
    tail := 0.U
    count := 0.U
  } .otherwise {
    when (popStored) { head := nextHead }
    when (pushStored) {
      words(tail) := response
      tail := tail + 1.U
    }
    when (pushStored =/= popStored) {
      count := Mux(pushStored, count + 1.U, count - 1.U)
    }
  }

  // Reserve room before issuing. Drain old requests with their old VA so
  // neither their data nor their faults can join the redirected stream.
  val room = count +& responseValid.asUInt < depth.U
  // A global flush may also change privilege or translation state at the edge.
  // Predictions and decode branch recovery retain the current MMU context.
  val steerRequest = redirect && !io.contextFlush && !requestActive
  io.rom.en := requestActive || steerRequest || (room && !faultBlocked)
  io.rom.addr := Mux(steerRequest, alignedTarget, requestAddr)
  io.rom.wen := 0.U
  io.rom.wdata := 0.U
  val accepted = io.rom.en && io.rom.valid
  pending := accepted
  when (accepted) {
    pendingKilled := !steerRequest && (requestKilled || cancel)
    pendingPageFault := io.rom.fault
    pendingAccessFault := io.rom.accessFault
  }
  // An idle request port can fetch the target in this cycle. Waiting an extra
  // cycle here would add a bubble to every predicted taken branch.
  when (steerRequest) {
    restartAddr := alignedTarget
    requestActive := !accepted
    requestKilled := false.B
    requestAddr := Mux(accepted, alignedTarget + 4.U, alignedTarget)
  } .elsewhen (cancel) {
    restartAddr := alignedTarget
    when (io.rom.en && !accepted) {
      requestActive := true.B
      requestKilled := true.B
    } .otherwise {
      requestActive := false.B
      requestKilled := false.B
      requestAddr := alignedTarget
    }
  } .elsewhen (accepted) {
    requestActive := false.B
    requestKilled := false.B
    requestAddr := Mux(requestKilled, restartAddr, requestAddr + 4.U)
  } .elsewhen (io.rom.en) {
    requestActive := true.B
  }
}
