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
  val (sIdle :: sReadAddr :: sReadData :: sWriteAddr :: sWriteData ::
       sReadEnd :: sWriteEnd :: Nil) = Enum(7)
  val state = RegInit(sIdle)

  // AXI control
  val rdata   = Reg(UInt(DATA_WIDTH.W))
  val arvalid = state === sReadAddr
  val awvalid = state === sWriteAddr
  val rvalid  = io.axi.readData.valid && io.axi.readData.bits.last
  val wvalid  = state === sWriteData

  // main finite state machine
  switch (state) {
    is (sIdle) {
      when (io.sram.en) {
        state := Mux(io.sram.wen =/= 0.U, sWriteAddr, sReadAddr)
      }
    }
    is (sReadAddr) {
      when (io.axi.readAddr.ready) {
        state := sReadData
      }
    }
    is (sReadData) {
      when (rvalid) {
        rdata := io.axi.readData.bits.data
        state := sReadEnd
      }
    }
    is (sWriteAddr) {
      when (io.axi.writeAddr.ready) {
        state := sWriteData
      }
    }
    is (sWriteData) {
      when (io.axi.writeData.ready) {
        state := sWriteEnd
      }
    }
    is (sReadEnd) {
      state := sIdle
    }
    is (sWriteEnd) {
      state := sIdle
    }
  }

  // SRAM signals
  io.sram.valid := state === sWriteEnd || rvalid
  io.sram.fault := false.B
  io.sram.rdata := rdata

  // AXI signals
  io.axi.init()
  io.axi.readAddr.valid       := arvalid
  io.axi.readAddr.bits.addr   := io.sram.addr
  io.axi.readAddr.bits.size   := dataSize.U
  io.axi.readAddr.bits.burst  := 1.U          // incrementing-address
  io.axi.readData.ready       := true.B
  io.axi.writeAddr.valid      := awvalid
  io.axi.writeAddr.bits.addr  := io.sram.addr
  io.axi.writeAddr.bits.size  := dataSize.U
  io.axi.writeData.valid      := wvalid
  io.axi.writeData.bits.data  := io.sram.wdata
  io.axi.writeData.bits.last  := wvalid
  io.axi.writeData.bits.strb  := io.sram.wen
  io.axi.writeResp.ready      := true.B
}
