package bus

import chisel3._
import chisel3.util._

import io._
import consts.Parameters._
import axi.AxiMaster

// direct mapped instruction cache
class InstCache extends Module {
  val io = IO(new Bundle {
    // SRAM interface
    val sram  = Flipped(new SramIO(ADDR_WIDTH, INST_WIDTH))
    // control signals
    val flush = Input(Bool())
    // AXI interface
    val axi   = new AxiMaster(ADDR_WIDTH, INST_WIDTH)
  })

  // some constants
  val dataMemSize = ICACHE_LINE_SIZE / (INST_WIDTH / 8)
  val tagWidth    = ADDR_WIDTH - ICACHE_LINE_WIDTH - ICACHE_WIDTH
  val burstSize   = log2Ceil(INST_WIDTH / 8)
  val burstLen    = dataMemSize - 1
  require(log2Ceil(burstLen) <= io.axi.readAddr.bits.len.getWidth)

  // states of finite state machine
  val sIdle :: sAddr :: sData :: sUpdate :: Nil = Enum(4)
  val state = RegInit(sIdle)

  // all cache lines
  val valid = RegInit(VecInit(Seq.fill(ICACHE_SIZE) { false.B }))
  val tag   = Mem(ICACHE_SIZE, UInt(tagWidth.W))
  val lines = SyncReadMem(ICACHE_SIZE * dataMemSize, UInt(INST_WIDTH.W))

  // AXI control
  val ren         = RegInit(false.B)
  val raddr       = Reg(UInt(ADDR_WIDTH.W))
  val dataOffset  = Reg(UInt(log2Ceil(dataMemSize).W))

  // cache line selectors
  val sramAddr    = Reg(UInt(ADDR_WIDTH.W))
  val selAddr     = Mux(state === sIdle, io.sram.addr, sramAddr)
  val tagSel      = selAddr(ADDR_WIDTH - 1,
                            ICACHE_WIDTH + ICACHE_LINE_WIDTH)
  val lineSel     = selAddr(ICACHE_WIDTH + ICACHE_LINE_WIDTH - 1,
                            ICACHE_LINE_WIDTH)
  val lineDataSel = selAddr(ICACHE_WIDTH + ICACHE_LINE_WIDTH - 1,
                            burstSize)
  val dataSel     = Cat(lineSel, dataOffset)
  val startAddr   = Cat(tagSel, lineSel, 0.U(ICACHE_LINE_WIDTH.W))
  val cacheHit    = valid(lineSel) && tag(lineSel) === tagSel

  // main finite state machine
  switch (state) {
    is (sIdle) {
      // cache idle
      when (io.flush) {
        // flush all valid bits
        valid.foreach(v => v := false.B)
        // reset state
        state := sIdle
      } .elsewhen (io.sram.en && !cacheHit) {
        // cache miss, switch state
        ren       := true.B
        raddr     := startAddr
        sramAddr  := io.sram.addr
        state     := sAddr
      }
    }
    is (sAddr) {
      // send read address to bus
      when (io.axi.readAddr.ready) {
        // address has already been sent, switch state
        ren         := false.B
        dataOffset  := 0.U
        state       := sData
      }
    }
    is (sData) {
      // fetch data from bus
      when (io.axi.readData.valid) {
        dataOffset := dataOffset + 1.U
        lines.write(dataSel, io.axi.readData.bits.data)
      }
      // switch state
      when (io.axi.readData.valid && io.axi.readData.bits.last) {
        state := sUpdate
      }
    }
    is (sUpdate) {
      // update cache line & make data ready
      valid(lineSel)  := true.B
      tag(lineSel)    := tagSel
      state           := sIdle
    }
  }

  // SRAM signals
  io.sram.valid := state === sIdle && cacheHit
  io.sram.fault := false.B
  io.sram.rdata := lines.read(lineDataSel)

  // AXI signals
  io.axi.init()
  io.axi.readAddr.valid       := ren
  io.axi.readAddr.bits.addr   := raddr
  io.axi.readAddr.bits.size   := burstSize.U  // bytes per beat
  io.axi.readAddr.bits.len    := burstLen.U   // beats per burst
  io.axi.readAddr.bits.burst  := 1.U          // incrementing-address
  io.axi.readData.ready       := true.B
}
