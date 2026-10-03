package io

import chisel3._

// SRAM interface
class SramIO(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  // enable signal
  val en    = Output(Bool())
  // data valid flag
  val valid = Input(Bool())
  // page fault flag
  val fault = Input(Bool())
  // physical memory/bus access fault flag
  val accessFault = Input(Bool())
  // other signals
  val wen   = Output(UInt((dataWidth / 8).W))
  val addr  = Output(UInt(addrWidth.W))
  val rdata = Input(UInt(dataWidth.W))
  val wdata = Output(UInt(dataWidth.W))
}

// TLB control
class TlbControlIO(val ppnWidth: Int) extends Bundle {
  // enable address translation
  val en        = Output(Bool())
  // Data accesses can use MPP through MPRV while instruction fetch stays in M.
  val dataEn    = Output(Bool())
  val dataSmode = Output(Bool())
  val mxr       = Output(Bool())
  // Architectural store intent, independent of the current byte strobes.
  val dataWrite = Output(Bool())
  // flush signals
  val flushInst = Output(Bool())
  val flushData = Output(Bool())
  // base PPN of page table
  val basePpn   = Output(UInt(ppnWidth.W))
  // permit S-mode user memory access
  val sum       = Output(Bool())
  val smode     = Output(Bool())
}

// cache control
class CacheControlIO extends Bundle {
  val flushInst = Output(Bool())
  val flushData = Output(Bool())
  val flushDataDone = Input(Bool())
  val flushDataAccessFault = Input(Bool())
}

// interrupt request
class InterruptIO extends Bundle {
  val timer   = Input(Bool())
  val soft    = Input(Bool())
  val extern  = Input(Bool())
  val supervisorExtern = Input(Bool())
}
