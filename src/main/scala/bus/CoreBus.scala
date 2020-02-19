package bus

import chisel3._

import io._
import consts.Parameters._
import consts.Paging.PPN_WIDTH
import axi.AxiMaster

/*

Structure of core bus:

    +-----+       +-----+
    | ROM |       | RAM |
    +-----+       +-----+
       |             |
      / \           / \
     /   \         /   \
    | +------+    | +------+
    | | ITLB |    | | DTLB |
    | +------+    | +------+
    |    |        |    |
  +-------+     +-------+
  | demux |     | demux |
  +-------+     +-------+
      |             |
      |          +-----+
      |          | mux |
      |          +-----+
      |           /   \
    +----+    +----+ +----------+
    | I$ |    | D$ | | uncached |
    +----+    +----+ +----------+
      |         |         |
  +------+  +------+   +------+
  | IAXI |  | DAXI |   | UAXI |
  +------+  +------+   +------+

*/

class CoreBus extends Module {
  val io = IO(new Bundle {
    // SRAM interface for core
    val rom       = Flipped(new SramIO(ADDR_WIDTH, INST_WIDTH))
    val ram       = Flipped(new SramIO(ADDR_WIDTH, DATA_WIDTH))
    // control channels
    val tlb       = Flipped(new TlbControlIO(PPN_WIDTH))
    val cache     = Flipped(new CacheControlIO)
    // instruction AXI interface
    val inst      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    // data AXI interface
    val data      = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
    // uncached AXI interface
    val uncached  = new AxiMaster(ADDR_WIDTH, DATA_WIDTH)
  })

  // Assert instruction width is equal to data width.
  // Because only in this case, PTE & instruction can
  // be both stored in the I-cache.
  require(INST_WIDTH == DATA_WIDTH)

  // instruction MMU
  val immu = Module(new MMU(ITLB_SIZE, true))
  immu.io.en      := io.tlb.en
  immu.io.flush   := io.tlb.flushInst
  immu.io.basePpn := io.tlb.basePpn
  immu.io.sum     := io.tlb.sum
  immu.io.smode   := io.tlb.smode
  immu.io.lookup  := io.rom.en
  immu.io.write   := io.rom.wen =/= 0.U
  immu.io.vaddr   := io.rom.addr

  // demux for I-MMU's data interface & ROM interface
  val idemux = Module(new SramDemux2(ADDR_WIDTH, DATA_WIDTH))
  idemux.io.sel2      := immu.io.valid
  idemux.io.in1       <> immu.io.data
  idemux.io.in2       <> io.rom
  idemux.io.in2.addr  := immu.io.paddr

  // instruction cache
  val icache = Module(new InstCache)
  icache.io.sram  <> idemux.io.out
  icache.io.flush := io.cache.flushInst
  icache.io.axi   <> io.inst

  // ROM interface
  io.rom.fault  := immu.io.fault

  // data MMU
  val dmmu = Module(new MMU(DTLB_SIZE, false))
  dmmu.io.en      := io.tlb.en
  dmmu.io.flush   := io.tlb.flushData
  dmmu.io.basePpn := io.tlb.basePpn
  dmmu.io.sum     := io.tlb.sum
  dmmu.io.smode   := io.tlb.smode
  dmmu.io.lookup  := io.ram.en
  dmmu.io.write   := io.ram.wen =/= 0.U
  dmmu.io.vaddr   := io.ram.addr

  // demux for D-MMU's data interface & RAM interface
  val ddemux = Module(new SramDemux2(ADDR_WIDTH, DATA_WIDTH))
  ddemux.io.sel2      := dmmu.io.valid
  ddemux.io.in1       <> dmmu.io.data
  ddemux.io.in2       <> io.ram
  ddemux.io.in2.addr  := dmmu.io.paddr

  // mux to D-cache & uncached
  val mux = Module(new SramMux2(ADDR_WIDTH, DATA_WIDTH))
  mux.io.sel2 := dmmu.io.paddr >= UNCACHED_ADDR_START &&
                 dmmu.io.paddr < UNCACHED_ADDR_END
  mux.io.in   <> ddemux.io.out

  // data cache
  val dcache = Module(new DataCache)
  dcache.io.sram  <> mux.io.out1
  dcache.io.flush := io.cache.flushData
  dcache.io.axi   <> io.data

  // data uncached
  val uncached = Module(new Uncached)
  uncached.io.sram  <> mux.io.out2
  uncached.io.axi   <> io.uncached

  // RAM interface
  io.ram.fault  := dmmu.io.fault
}
