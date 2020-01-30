package lsu

import chisel3._
import chisel3.util._

import LsuDecode._
import consts.Parameters._

class AmoExecute extends Module {
  val io = IO(new Bundle {
    // control signals
    val op        = Input(UInt(AMO_OP_WIDTH.W))
    val flush     = Input(Bool())
    val ready     = Output(Bool())
    // data from/to regfile (lsuData/result)
    val regOpr    = Input(UInt(DATA_WIDTH.W))
    val regWdata  = Output(UInt(DATA_WIDTH.W))
    // RAM control
    val ramValid  = Input(Bool())
    val ramWen    = Output(Bool())
    val ramRdata  = Input(UInt(DATA_WIDTH.W))
    val ramWdata  = Output(UInt(DATA_WIDTH.W))
  })
  
  // state of finite state machine
  val sIdle :: sStore :: sEnd :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // operands
  val opr1 = io.ramRdata
  val opr2 = io.regOpr

  // generate execute result
  val result = MuxLookup(io.op, 0.U, Seq(
    AMO_OP_SWAP -> opr2,
    AMO_OP_ADD  -> (opr1 + opr2),
    AMO_OP_XOR  -> (opr1 ^ opr2),
    AMO_OP_AND  -> (opr1 & opr2),
    AMO_OP_OR   -> (opr1 | opr2),
    AMO_OP_MIN  -> Mux(opr1.asSInt < opr2.asSInt, opr1, opr2),
    AMO_OP_MAX  -> Mux(opr1.asSInt > opr2.asSInt, opr1, opr2),
    AMO_OP_MINU -> Mux(opr1 < opr2, opr1, opr2),
    AMO_OP_MAXU -> Mux(opr1 > opr2, opr1, opr2),
  ))

  // finite state machine
  when (io.flush) {
    state := sIdle
  } .elsewhen (io.ramValid) {
    switch (state) {
      // send read request to RAM
      is (sIdle) {
        // switch to next state
        when (io.op === AMO_OP_NOP) {
          state := sIdle
        } .otherwise {
          state := sStore
        }
      }
      // store result to RAM
      is (sStore) {
        // switch to next state
        state := sEnd
      }
      // take a breath
      is (sEnd) {
        state := sIdle
      }
    }
  }

  // output signals
  io.ready    := state === sEnd
  io.regWdata := io.ramRdata
  io.ramWen   := state === sStore
  io.ramWdata := result
}
