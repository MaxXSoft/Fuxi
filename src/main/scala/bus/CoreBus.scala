package bus

import chisel3._
import org.chipsalliance.cde.config.Parameters
import configs.{ConfigValidation, HasCoreParams, HasPlatformParams, PlatformParamsKey}

import io._
import consts.Architecture._
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

class CoreBus(implicit val p: Parameters) extends Module
    with HasCoreParams with HasPlatformParams {
  // Cached and uncached routing must not split a D-cache line.
  ConfigValidation.validateDataUncachedRange(
    p(PlatformParamsKey).dataUncached, DCACHE_LINE_BYTES)

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
    // performance monitoring
    val pmu       = Output(new BusPerformanceIO)
  })

  // Assert instruction width is equal to data width.
  // Because only in this case, PTE & instruction can
  // be both stored in the I-cache.
  require(INST_WIDTH == DATA_WIDTH)

  // instruction MMU
  val immu = Module(new MMU(ITLB_ENTRIES, true))
  immu.io.en      := io.tlb.en
  immu.io.flush   := io.tlb.flushInst
  immu.io.basePpn := io.tlb.basePpn
  immu.io.sum     := io.tlb.sum
  immu.io.mxr     := false.B
  immu.io.smode   := io.tlb.smode
  immu.io.lookup  := io.rom.en
  immu.io.write   := io.rom.wen =/= 0.U
  immu.io.vaddr   := io.rom.addr

  // demux for I-MMU's data interface & ROM interface
  val idemux = Module(new SramDemux2(ADDR_WIDTH, DATA_WIDTH))
  // The walker owns the address port only during a PTE request in Sv32.
  // Otherwise the I-cache can index the demand address while the ITLB looks
  // up its tag. Bare fetches have priority even over a paused walk.
  val instWalkRequest = io.tlb.en && immu.io.data.en
  idemux.io.sel2      := !instWalkRequest
  idemux.io.in1       <> immu.io.data
  idemux.io.in2       <> io.rom
  idemux.io.in2.en    := io.rom.en && immu.io.valid && !immu.io.fault && !immu.io.accessFault
  idemux.io.in2.addr  := immu.io.paddr

  // instruction cache
  val icache = Module(new InstCache)
  icache.io.sram  <> idemux.io.out
  icache.io.flush := io.cache.flushInst
  icache.io.axi   <> io.inst

  // ROM interface
  // A warm candidate line can hit before translation completes.
  io.rom.valid       := immu.io.fault || immu.io.accessFault ||
                        (immu.io.valid && idemux.io.in2.valid)
  io.rom.fault       := immu.io.fault
  io.rom.accessFault := immu.io.accessFault || idemux.io.in2.accessFault

  // data MMU
  val dmmu = Module(new MMU(DTLB_ENTRIES, false))
  dmmu.io.en      := io.tlb.dataEn
  dmmu.io.flush   := io.tlb.flushData
  dmmu.io.basePpn := io.tlb.basePpn
  dmmu.io.sum     := io.tlb.sum
  dmmu.io.mxr     := io.tlb.mxr
  dmmu.io.smode   := io.tlb.dataSmode
  dmmu.io.lookup  := io.ram.en
  dmmu.io.write   := io.tlb.dataWrite || io.ram.wen =/= 0.U
  dmmu.io.vaddr   := io.ram.addr

  // demux for D-MMU's data interface & RAM interface
  val ddemux = Module(new SramDemux2(ADDR_WIDTH, DATA_WIDTH))
  ddemux.io.sel2      := dmmu.io.valid
  ddemux.io.in1       <> dmmu.io.data
  ddemux.io.in2       <> io.ram
  ddemux.io.in2.addr  := dmmu.io.paddr

  // mux to D-cache & uncached
  val mux = Module(new SramMux2(ADDR_WIDTH, DATA_WIDTH))
  mux.io.sel2 := dmmu.io.paddr >= DATA_UNCACHED_START.U &&
                 dmmu.io.paddr < DATA_UNCACHED_END.U
  mux.io.in   <> ddemux.io.out

  // data cache
  val dcache = Module(new DataCache)
  dcache.io.sram  <> mux.io.out1
  dcache.io.flush := io.cache.flushData
  dcache.io.axi   <> io.data
  io.cache.flushDataDone        := dcache.io.flushDone
  io.cache.flushDataAccessFault := dcache.io.flushAccessFault

  // data uncached
  val uncached = Module(new Uncached)
  uncached.io.sram  <> mux.io.out2
  uncached.io.axi   <> io.uncached

  // RAM interface
  io.ram.valid       := dmmu.io.accessFault || ddemux.io.in2.valid
  io.ram.fault       := dmmu.io.fault
  io.ram.accessFault := dmmu.io.accessFault || ddemux.io.in2.accessFault

  // performance monitoring
  io.pmu.icache           := icache.io.pmu
  io.pmu.icacheWalkAccess := icache.io.pmu.access && instWalkRequest
  io.pmu.icacheWalkMiss   := icache.io.pmu.miss && instWalkRequest
  io.pmu.dcache           := dcache.io.pmu
  io.pmu.dcacheWalkAccess := dcache.io.pmu.access && !ddemux.io.sel2
  io.pmu.dcacheWalkMiss   := dcache.io.pmu.miss && !ddemux.io.sel2
  io.pmu.itlb             := immu.io.pmu
  io.pmu.dtlb             := dmmu.io.pmu
}
