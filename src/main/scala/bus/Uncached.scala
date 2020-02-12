package bus

import chisel3._
import chisel3.util._

import io._
import consts.Parameters._
import axi.AxiMaster

// uncached memory accessing
class Uncached extends Module {
  val io = IO(new Bundle {
    // SRAM interface
    val sram  = Flipped(new SramIO(ADDR_WIDTH, DATA_WIDTH))
    // AXI interface
    val axi   = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })

  // some constants
  val dataSize = log2Ceil(DATA_WIDTH / 8)

  // states of finite state machine
  val (sIdle :: sReadAddr :: sReadData ::
       sWriteAddr :: sWriteData :: Nil) = Enum(5)
  val state = RegInit(sIdle)

  // AXI control
  val addr    = Reg(UInt(ADDR_WIDTH.W))
  val wen     = RegInit(false.B)
  val rdata   = Reg(UInt(DATA_WIDTH.W))
  val arvalid = state === sReadAddr &&io.axi.readAddr.ready
  val awvalid = state === sWriteAddr &&io.axi.writeAddr.ready

  // main finite state machine
  switch (state) {
    is (sIdle) {
      when (io.sram.en) {
        addr  := io.sram.addr
        state := Mux(io.sram.wen =/= 0.U, sWriteAddr, sReadAddr)
      }
    }
    is (sReadAddr) {
      when (io.axi.readAddr.ready) {
        state := sReadData
      }
    }
    is (sReadData) {
      when (io.axi.readData.valid && io.axi.readData.bits.last) {
        rdata := io.axi.readData.bits.data
        state := sIdle
      }
    }
    is (sWriteAddr) {
      when (io.axi.writeAddr.ready) {
        state := sWriteData
      }
    }
    is (sWriteData) {
      wen := io.axi.writeData.ready && !io.axi.writeData.bits.last
      when (io.axi.writeData.bits.last) {
        state := sIdle
      }
    }
  }

  // SRAM signals
  io.sram.valid := state === sIdle
  io.sram.fault := false.B
  io.sram.rdata := rdata

  // AXI signals
  io.axi.init()
  io.axi.readAddr.valid       := arvalid
  io.axi.readAddr.bits.addr   := addr
  io.axi.readAddr.bits.size   := dataSize.U
  io.axi.readData.ready       := true.B
  io.axi.writeAddr.valid      := awvalid
  io.axi.writeAddr.bits.addr  := addr
  io.axi.writeAddr.bits.size  := dataSize.U
  io.axi.writeData.valid      := wen
  io.axi.writeData.bits.data  := io.sram.wdata
  io.axi.writeData.bits.last  := wen
  io.axi.writeData.bits.strb  := io.sram.wen
  io.axi.writeResp.ready      := true.B
}
