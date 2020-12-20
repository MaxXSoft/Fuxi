package bus

import chisel3._
import chisel3.util._

import io._
import consts.Parameters._
import axi.AxiMaster
import utils.BlockMem

// direct mapped data cache
class DataCache extends Module {
  val io = IO(new Bundle {
    // SRAM interface
    val sram  = Flipped(new SramIO(ADDR_WIDTH, DATA_WIDTH))
    // control signals
    val flush = Input(Bool())
    // AXI interface
    val axi   = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })

  // some constants
  val dataMemSize = DCACHE_LINE_SIZE / (DATA_WIDTH / 8)
  val tagWidth    = ADDR_WIDTH - DCACHE_LINE_WIDTH - DCACHE_WIDTH
  val burstSize   = log2Ceil(DATA_WIDTH / 8)
  val burstLen    = dataMemSize - 1
  val wordWriteEn = (1 << (DATA_WIDTH / 8)) - 1
  require(log2Ceil(burstLen) <= io.axi.readAddr.bits.len.getWidth)

  // states of finite state machine
  val (sIdle :: sReadAddr :: sReadData :: sReadUpdate ::
       sWriteAddr :: sWriteData ::
       sFlushAddr :: sFlushData :: sFlushUpdate :: Nil) = Enum(9)
  val state = RegInit(sIdle)

  // all cache lines
  val valid = RegInit(VecInit(Seq.fill(DCACHE_SIZE) { false.B }))
  val dirty = Reg(Vec(DCACHE_SIZE, Bool()))
  val tag   = Mem(DCACHE_SIZE, UInt(tagWidth.W))
  val lines = Module(new BlockMem(DCACHE_SIZE * dataMemSize, DATA_WIDTH))

  // AXI control
  val dataOffset  = Reg(UInt(log2Ceil(dataMemSize + 1).W))
  val lastOffset  = Reg(UInt(log2Ceil(dataMemSize + 1).W))
  val dataOfsRef  = dataOffset(log2Ceil(dataMemSize) - 1, 0)
  val aren        = RegInit(false.B)
  val raddr       = Reg(UInt(ADDR_WIDTH.W))
  val awen        = RegInit(false.B)
  val waddr       = Reg(UInt(ADDR_WIDTH.W))
  val wen         = RegInit(false.B)
  val wlast       = wen && dataOffset === (burstLen + 1).U

  // cache line selectors
  val sramAddr    = Reg(UInt(ADDR_WIDTH.W))
  val selAddr     = Mux(state === sIdle, io.sram.addr, sramAddr)
  val tagSel      = selAddr(ADDR_WIDTH - 1,
                            DCACHE_WIDTH + DCACHE_LINE_WIDTH)
  val lineSel     = selAddr(DCACHE_WIDTH + DCACHE_LINE_WIDTH - 1,
                            DCACHE_LINE_WIDTH)
  val lineDataSel = selAddr(DCACHE_WIDTH + DCACHE_LINE_WIDTH - 1,
                            burstSize)
  val dataSel     = Cat(lineSel, dataOfsRef)
  val startRaddr  = Cat(tagSel, lineSel, 0.U(DCACHE_LINE_WIDTH.W))
  val startWaddr  = Cat(tag(lineSel), lineSel, 0.U(DCACHE_LINE_WIDTH.W))

  // cache state & flush selector
  val cacheHit  = valid(lineSel) && tag(lineSel) === tagSel
  val realDirty = 0 until DCACHE_SIZE map { i => valid(i) && dirty(i) }
  val isDirty   = realDirty.reduce(_||_)
  val nextDirty = PriorityEncoder(realDirty)(DCACHE_WIDTH - 1, 0)
  val flushAddr = Cat(tag(nextDirty), nextDirty, 0.U(DCACHE_LINE_WIDTH.W))
  val flushSel  = Cat(nextDirty, dataOfsRef)

  // read from / write to cache line
  val lineEnCpu   = state === sIdle && !io.flush && io.sram.en && cacheHit
  val lineEnRead  = state === sReadData && io.axi.readData.valid
  val lineEnWrite = state === sWriteData
  val lineEnFlush = state === sFlushData
  lines.io.en     := true.B
  lines.io.wen    := Mux(lineEnCpu, io.sram.wen,
                     Mux(lineEnRead, wordWriteEn.U, 0.U))
  lines.io.addr   := Mux(lineEnRead || lineEnWrite, dataSel,
                     Mux(lineEnFlush, flushSel, lineDataSel))
  lines.io.wdata  := Mux(lineEnCpu, io.sram.wdata,
                                    io.axi.readData.bits.data)

  // main finite state machine
  switch (state) {
    is (sIdle) {
      // cache idle
      when (io.flush && isDirty) {
        // flush next dirty cache line
        state := sFlushAddr
      } .elsewhen (io.sram.en) {
        when (cacheHit) {
          // write data to cache line
          when (io.sram.wen =/= 0.U) {
            // set dirty bit
            dirty(lineSel) := true.B
          }
        } .elsewhen (valid(lineSel) && dirty(lineSel)) {
          // write dirty to memory and invalidate
          sramAddr := io.sram.addr
          state := sWriteAddr
        } .otherwise {
          // read from memory
          sramAddr := io.sram.addr
          state := sReadAddr
        }
      }
    }
    is (sReadAddr) {
      // send read address to bus
      aren := true.B
      raddr := startRaddr
      // switch state
      when (aren && io.axi.readAddr.ready) {
        aren := false.B
        dataOffset := 0.U
        state := sReadData
      }
    }
    is (sReadData) {
      // fetch data from bus
      when (io.axi.readData.valid) {
        dataOffset := dataOffset + 1.U
      }
      // switch state
      when (io.axi.readData.valid && io.axi.readData.bits.last) {
        valid(lineSel) := true.B
        dirty(lineSel) := false.B
        tag(lineSel) := tagSel
        state := sReadUpdate
      }
    }
    is (sReadUpdate) {
      // wait for 'rdata' to be ready
      state := sIdle
    }
    is (sWriteAddr) {
      // send write address to bus
      awen := true.B
      waddr := startWaddr
      // switch state
      when (awen && io.axi.writeAddr.ready) {
        awen := false.B
        dataOffset := 0.U
        lastOffset := 0.U
        state := sWriteData
      }
    }
    is (sWriteData) {
      // send write data to bus
      when (io.axi.writeData.ready && !wlast) {
        wen := true.B
        dataOffset := dataOffset + 1.U
        lastOffset := dataOffset
      } .otherwise {
        wen := false.B
        dataOffset := lastOffset
      }
      // switch state
      when (wlast) {
        wen := false.B
        valid(lineSel) := false.B
        state := sReadAddr
      }
    }
    is (sFlushAddr) {
      // send flush (write) address to bus
      awen := true.B
      waddr := flushAddr
      // switch state
      when (awen && io.axi.writeAddr.ready) {
        awen := false.B
        dataOffset := 0.U
        state := sFlushData
      }
    }
    is (sFlushData) {
      // send flush (write) data to bus
      when (io.axi.writeData.ready && !wlast) {
        wen := true.B
        dataOffset := dataOffset + 1.U
      } .otherwise {
        wen := false.B
      }
      // switch state
      when (wlast) {
        wen := false.B
        valid(nextDirty) := false.B
        state := sFlushUpdate
      }
    }
    is (sFlushUpdate) {
      // determine if need to flush next line
      state := Mux(isDirty, sFlushAddr, sIdle)
    }
  }

  // SRAM signals
  io.sram.valid := state === sIdle && Mux(io.flush, !isDirty, cacheHit)
  io.sram.fault := false.B
  io.sram.rdata := lines.io.rdata

  // AXI signals
  io.axi.init()
  io.axi.readAddr.valid       := aren
  io.axi.readAddr.bits.addr   := raddr
  io.axi.readAddr.bits.size   := burstSize.U  // bytes per beat
  io.axi.readAddr.bits.len    := burstLen.U   // beats per burst
  io.axi.readAddr.bits.burst  := 1.U          // incrementing-address
  io.axi.readData.ready       := true.B
  io.axi.writeAddr.valid      := awen
  io.axi.writeAddr.bits.addr  := waddr
  io.axi.writeAddr.bits.size  := burstSize.U  // bytes per beat
  io.axi.writeAddr.bits.len   := burstLen.U   // beats per burst
  io.axi.writeAddr.bits.burst := 1.U          // incrementing-address
  io.axi.writeData.valid      := wen
  io.axi.writeData.bits.data  := lines.io.rdata
  io.axi.writeData.bits.last  := wlast
  io.axi.writeData.bits.strb  := wordWriteEn.U
  io.axi.writeResp.ready      := true.B
}
