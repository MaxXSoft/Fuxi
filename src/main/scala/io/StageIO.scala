package io

import chisel3._

import consts.Constants._
import consts.Instructions

// interface of stage's IO
class StageIO[T <: StageIO[T]] extends Bundle {
  this: T =>

  // for initializing flip-flops in mid-stage
  def default() = 0.U.asTypeOf(this)
}

// IF stage
class FetchIO extends StageIO[FetchIO] {
  val inst      = UInt(INST_WIDTH.W)
  val pc        = UInt(ADDR_WIDTH.W)
  val predIndex = UInt(ADDR_WIDTH.W)

  override def default() = {
    val init = Wire(new FetchIO)
    init.inst := Instructions.NOP
    init.pc := 0.U
    init.predIndex := 0.U
    init
  }
}

// ID stage
class DecoderIO extends StageIO[DecoderIO] {
  //
}
