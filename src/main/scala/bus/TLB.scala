package bus

import chisel3._
import chisel3.util.{isPow2, log2Ceil}

import consts.Parameters._
import consts.Paging._

class TlbEntry extends Bundle {
  val ppn = UInt(PPN_WIDTH.W)
  val d   = Bool()
  val a   = Bool()
  val u   = Bool()
  val x   = Bool()
  val w   = Bool()
  val r   = Bool()
}

class TLB(val size: Int) extends Module {
  val io = IO(new Bundle {
    // control signals
    val flush = Input(Bool())
    // write/lookup channel
    val wen   = Input(Bool())
    val vaddr = Input(UInt(ADDR_WIDTH.W))
    val went  = Input(new TlbEntry)
    val valid = Output(Bool())
    val rent  = Output(new TlbEntry)
  })

  // some constants
  require(isPow2(size), "TLB size must be a power of 2")
  val width = log2Ceil(size)

  // all TLB entries
  val valid = RegInit(VecInit(Seq.fill(size) { false.B }))
  val data  = Mem(size, new Bundle {
    val vpn   = UInt(VPN_WIDTH.W)
    val entry = new TlbEntry
  })

  // pointer to the TLB entry to be written
  val pointer = RegInit(0.U(width.W))
  val vpn     = io.vaddr(ADDR_WIDTH - 1, PAGE_OFFSET_WIDTH)

  // TLB flush/write
  when (io.flush) {
    // flush all valid bits
    valid.foreach(v => v := false.B)
  } .elsewhen (io.wen) {
    // write valid bit & data
    valid(pointer)      := true.B
    data(pointer).vpn   := vpn
    data(pointer).entry := io.went
    // increase pointer
    pointer := pointer + 1.U
  }

  // TLB lookup
  val found = WireInit(false.B)
  val entry = WireInit(0.U.asTypeOf(new TlbEntry))
  for (i <- 0 until size) {
    when (valid(i) && data(i).vpn === vpn) {
      found := true.B
      entry := data(i).entry
    }
  }

  // output signals
  io.valid  := found
  io.rent   := entry
}
