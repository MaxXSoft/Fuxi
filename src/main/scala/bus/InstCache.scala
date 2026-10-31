package bus

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.CoreParamsKey
import chisel3.util._

import io._
import consts.Architecture._
import axi.AxiMaster

// direct mapped instruction cache
class InstCache(implicit val p: Parameters) extends Module {
  val params = p(CoreParamsKey).icache

  val io = IO(new Bundle {
    // SRAM interface
    val sram  = Flipped(new SramIO(ADDR_WIDTH, INST_WIDTH))
    // control signals
    val flush = Input(Bool())
    // AXI interface
    val axi   = new AxiMaster(ADDR_WIDTH, INST_WIDTH)
    // performance monitoring
    val pmu   = Output(new CachePerformanceIO)
  })

  // some constants
  val dataMemSize = params.lineBytes / (INST_WIDTH / 8)
  val tagWidth    = ADDR_WIDTH - params.offsetBits - params.indexBits
  val burstSize   = log2Ceil(INST_WIDTH / 8)
  val burstLen    = dataMemSize - 1
  require(log2Ceil(burstLen) <= io.axi.readAddr.bits.len.getWidth)

  // states of finite state machine
  object State extends ChiselEnum {
    val sIdle, sAddr, sData, sUpdate, sAccessFault = Value
  }
  import State._
  val state = RegInit(sIdle)

  // all cache lines
  val valid = RegInit(VecInit(Seq.fill(params.lines) { false.B }))
  val tag   = Mem(params.lines, UInt(tagWidth.W))
  val lines = SyncReadMem(params.lines * dataMemSize, UInt(INST_WIDTH.W))

  // AXI control
  val ren         = RegInit(false.B)
  val raddr       = Reg(UInt(ADDR_WIDTH.W))
  val dataOffset  = Reg(UInt(log2Ceil(dataMemSize).W))
  val readFault   = RegInit(false.B)
  val discardRefill = RegInit(false.B)

  // cache line selectors
  val sramAddr    = Reg(UInt(ADDR_WIDTH.W))
  val selAddr     = Mux(state === sIdle, io.sram.addr, sramAddr)
  val tagSel      = selAddr(ADDR_WIDTH - 1,
                            params.indexBits + params.offsetBits)
  val lineSel     = selAddr(params.indexBits + params.offsetBits - 1,
                            params.offsetBits)
  val lineDataSel = selAddr(params.indexBits + params.offsetBits - 1,
                            burstSize)
  val dataSel     = Cat(lineSel, dataOffset)
  val startAddr   = Cat(tagSel, lineSel, 0.U(params.offsetBits.W))
  val cacheHit    = valid(lineSel) && tag(lineSel) === tagSel

  // main finite state machine
  switch (state) {
    is (sIdle) {
      // cache idle
      when (io.flush) {
        state := sIdle
      } .elsewhen (io.sram.en && !cacheHit) {
        // cache miss, switch state
        valid(lineSel) := false.B
        ren       := true.B
        discardRefill := false.B
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
        readFault   := false.B
        state       := sData
      }
    }
    is (sData) {
      // fetch data from bus
      when (io.axi.readData.valid) {
        dataOffset := dataOffset + 1.U
        readFault := readFault || io.axi.readData.bits.resp(1)
        lines.write(dataSel, io.axi.readData.bits.data)
      }
      // switch state
      when (io.axi.readData.valid && io.axi.readData.bits.last) {
        state := Mux(readFault || io.axi.readData.bits.resp(1),
                     sAccessFault, sUpdate)
      }
    }
    is (sUpdate) {
      // update cache line & make data ready
      valid(lineSel)  := !discardRefill
      tag(lineSel)    := tagSel
      state           := sIdle
    }
    is (sAccessFault) {
      // The partially refilled line stays invalid.
      state := sIdle
    }
  }

  // FENCE.I can finish while AXI is stalled. Invalidate every old line
  // immediately, then drain (without installing) any outstanding refill.
  // Keep ARVALID and the response handshake intact until the burst ends.
  when (io.flush) {
    valid.foreach(v => v := false.B)
    when (state =/= sIdle) { discardRefill := true.B }
  }

  // SRAM signals
  // A context change can finish the old virtual lookup before its refill.
  // Deliver a refill error only to the physical lookup that started it.
  val accessFault = state === sAccessFault && !discardRefill && !io.flush &&
                    io.sram.en && io.sram.addr === sramAddr
  io.sram.valid       := (state === sIdle && cacheHit && !io.flush) || accessFault
  io.sram.fault       := false.B
  io.sram.accessFault := accessFault
  io.sram.rdata       := lines.read(lineDataSel)

  // AXI signals
  io.axi.init()
  io.axi.readAddr.valid       := ren
  io.axi.readAddr.bits.addr   := raddr
  io.axi.readAddr.bits.size   := burstSize.U  // bytes per beat
  io.axi.readAddr.bits.len    := burstLen.U   // beats per burst
  io.axi.readAddr.bits.burst  := 1.U          // incrementing-address
  io.axi.readData.ready       := state === sData

  // performance monitoring
  io.pmu.access := io.sram.en && io.sram.valid
  io.pmu.miss := state === sIdle && !io.flush && io.sram.en && !cacheHit
  io.pmu.busy := state =/= sIdle
  io.pmu.writeback := false.B
}
