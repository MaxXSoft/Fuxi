package csr

import chisel3._
import chisel3.util._

import io._
import csr._
import consts.Parameters._
import consts.ExceptCause._
import consts.CSR._
import consts.CsrOp._
import consts.Control.{Y, N}
import consts.Paging.PPN_WIDTH
import consts.PerfEvent

class CsrFile(val hpmCount: Int) extends Module {
  require(hpmCount >= 0 && hpmCount <= 29)

  val io = IO(new Bundle {
    // read channel
    val read    = Flipped(new CsrReadIO)
    // write channel
    val write   = Flipped(new CsrWriteIO)
    // interrupt request
    val irq     = new InterruptIO
    val rtcTime = Input(UInt(RTC_WIDTH.W))
    // exception information
    val except  = Input(new ExceptInfoIO)
    // Each local event indexes a pulse or an active wait-cycle level.
    val perfEvents = Input(new PerformanceEventsIO)
    // CSR status
    val hasInt  = Output(Bool())
    val busy    = Output(Bool())
    val mode    = Output(UInt(CSR_MODE_WIDTH.W))
    val sepc    = Output(UInt(ADDR_WIDTH.W))
    val mepc    = Output(UInt(ADDR_WIDTH.W))
    val trapVec = Output(UInt(ADDR_WIDTH.W))
    // CSR commit events
    val satpWritten = Output(Bool())
    // paging signals
    val pageEn  = Output(Bool())
    val dataPageEn = Output(Bool())
    val dataMode = Output(UInt(CSR_MODE_WIDTH.W))
    val mxr     = Output(Bool())
    val basePpn = Output(UInt(PPN_WIDTH.W))
    val sum     = Output(Bool())
  })

  // current privilege mode
  val mode      = RegInit(CSR_MODE_M)

  // definition of CSRs
  val mstatus   = RegInit(MstatusCsr.default())
  val misa      = MisaCsr.default()
  val medeleg   = RegInit(MedelegCsr.default())
  val mideleg   = RegInit(MidelegCsr.default())
  val mie       = RegInit(MieCsr.default())
  val mtvec     = RegInit(MtvecCsr.default())
  val mcounteren = RegInit(McounterenCsr.default(hpmCount))
  val mscratch  = RegInit(MscratchCsr.default())
  val mepc      = RegInit(MepcCsr.default())
  val mcause    = RegInit(McauseCsr.default())
  val mtval     = RegInit(MtvalCsr.default())
  val mipReal   = RegInit(MipCsr.default())
  val mip       = WireDefault(MipCsr.default())
  val mcycle    = RegInit(McycleCsr.default())
  val minstret  = RegInit(MinstretCsr.default())
  val mhpmcounters = Seq.fill(hpmCount)(RegInit(MhpmcounterCsr.default()))
  val mcountinhibit = RegInit(McountinhibitCsr.default(hpmCount))
  val mhpmevents = Seq.fill(hpmCount)(RegInit(MhpmeventCsr.default()))
  val sstatus   = SstatusCsr(mstatus)
  val sie       = SieCsr(mie.asUInt & mideleg.asUInt)
  val stvec     = RegInit(StvecCsr.default())
  val scounteren = RegInit(ScounterenCsr.default(hpmCount))
  val sscratch  = RegInit(SscratchCsr.default())
  val sepc      = RegInit(SepcCsr.default())
  val scause    = RegInit(ScauseCsr.default())
  val stval     = RegInit(StvalCsr.default())
  val sip       = WireDefault(SipCsr.default())
  val satp      = RegInit(SatpCsr.default())
  val cycle     = mcycle.asUInt
  val instret   = minstret.asUInt

  // all supported CSRs
  val default  =
  //                                                       writable
  //                                      csrData      readable|
  //                                         |              |  |
                                 List(0.U,                  N, N)
  var csrTable = Array(
    BitPat(CSR_CYCLE)         -> List(cycle(31, 0),         Y, N),
    BitPat(CSR_TIME)          -> List(io.rtcTime(31, 0),    Y, N),
    BitPat(CSR_INSTRET)       -> List(instret(31, 0),       Y, N),
    BitPat(CSR_CYCLEH)        -> List(cycle(63, 32),        Y, N),
    BitPat(CSR_TIMEH)         -> List(io.rtcTime(63, 32),   Y, N),
    BitPat(CSR_INSTRETH)      -> List(instret(63, 32),      Y, N),
    BitPat(CSR_SSTATUS)       -> List(sstatus.asUInt,       Y, Y),
    BitPat(CSR_SIE)           -> List(sie.asUInt,           Y, Y),
    BitPat(CSR_STVEC)         -> List(stvec.asUInt,         Y, Y),
    BitPat(CSR_SCOUNTEREN)    -> List(scounteren.asUInt,    Y, Y),
    BitPat(CSR_SSCRATCH)      -> List(sscratch.asUInt,      Y, Y),
    BitPat(CSR_SEPC)          -> List(sepc.asUInt,          Y, Y),
    BitPat(CSR_SCAUSE)        -> List(scause.asUInt,        Y, Y),
    BitPat(CSR_STVAL)         -> List(stval.asUInt,         Y, Y),
    BitPat(CSR_SIP)           -> List(sip.asUInt,           Y, Y),
    BitPat(CSR_SATP)          -> List(satp.asUInt,          Y, Y),
    BitPat(CSR_MVENDERID)     -> List(0.U,                  Y, N),
    BitPat(CSR_MARCHID)       -> List(0.U,                  Y, N),
    BitPat(CSR_MIMPID)        -> List(0.U,                  Y, N),
    BitPat(CSR_MHARTID)       -> List(0.U,                  Y, N),
    BitPat(CSR_MSTATUS)       -> List(mstatus.asUInt,       Y, Y),
    // RV32 high status: fixed little-endian, without H or double-trap state.
    BitPat(CSR_MSTATUSH)      -> List(0.U,                  Y, Y),
    BitPat(CSR_MISA)          -> List(misa.asUInt,          Y, Y),
    BitPat(CSR_MEDELEG)       -> List(medeleg.asUInt,       Y, Y),
    BitPat(CSR_MIDELEG)       -> List(mideleg.asUInt,       Y, Y),
    BitPat(CSR_MIE)           -> List(mie.asUInt,           Y, Y),
    BitPat(CSR_MTVEC)         -> List(mtvec.asUInt,         Y, Y),
    BitPat(CSR_MCOUNTEREN)    -> List(mcounteren.asUInt,    Y, Y),
    BitPat(CSR_MSCRATCH)      -> List(mscratch.asUInt,      Y, Y),
    BitPat(CSR_MEPC)          -> List(mepc.asUInt,          Y, Y),
    BitPat(CSR_MCAUSE)        -> List(mcause.asUInt,        Y, Y),
    BitPat(CSR_MTVAL)         -> List(mtval.asUInt,         Y, Y),
    BitPat(CSR_MIP)           -> List(mip.asUInt,           Y, Y),
    BitPat(CSR_MCYCLE)        -> List(cycle(31, 0),         Y, Y),
    BitPat(CSR_MINSTRET)      -> List(instret(31, 0),       Y, Y),
    BitPat(CSR_MCYCLEH)       -> List(cycle(63, 32),        Y, Y),
    BitPat(CSR_MINSTRETH)     -> List(instret(63, 32),      Y, Y),
    BitPat(CSR_MCOUNTINHIBIT) -> List(mcountinhibit.asUInt, Y, Y),
  ) ++ (0 until 4).map { n =>
    BitPat(pmpCfgAddr(n))     -> List(0.U,                  Y, Y)
  } ++ (0 until 16).map { n =>
    BitPat(pmpAddr(n))        -> List(0.U,                  Y, Y)
  } ++ (3 to 31).flatMap { n =>
    // Absent machine slots intentionally remain writable WARL-zero CSRs:
    // reads return zero and writes are ignored by the implemented-slot loop.
    // Their user aliases are read-only, just like implemented counter aliases.
    val counter = if (n < hpmCount + 3) mhpmcounters(n - 3).asUInt else 0.U(64.W)
    val event = if (n < hpmCount + 3) mhpmevents(n - 3).asUInt else 0.U(32.W)
    Seq(
      BitPat(hpmCounterAddr(n)) -> List(counter(31, 0), Y, N),
      BitPat(hpmCounterHighAddr(n)) -> List(counter(63, 32), Y, N),
      BitPat(mhpmCounterAddr(n)) -> List(counter(31, 0), Y, Y),
      BitPat(mhpmCounterHighAddr(n)) -> List(counter(63, 32), Y, Y),
      BitPat(mhpmEventAddr(n)) -> List(event, Y, Y),
    )
  }

  // CSR read related signals
  val readData :: (readable: Bool) :: (writable: Bool) :: Nil =
      ListLookup(io.read.addr, default, csrTable)
  val readValid = MuxLookup(io.read.op, false.B)(Seq(
    CSR_R   -> readable,
    CSR_W   -> writable,
    CSR_RW  -> (readable && writable),
    CSR_RS  -> (readable && writable),
    CSR_RC  -> (readable && writable),
  ))
  val modeValid = requiredPrivilege(io.read.addr) <= mode
  // Low/high counter aliases share their enable bit; M-mode bypasses both.
  val readCounterIndex = counterIndex(io.read.addr)
  val counterValid = mode === CSR_MODE_M || !isUserCounter(io.read.addr) ||
    (mcounteren.data(readCounterIndex) && (mode =/= CSR_MODE_U || scounteren.data(readCounterIndex)))
  val valid = readValid && modeValid && counterValid

  // CSR write related signals
  val csrData :: _ :: _ :: Nil =
      ListLookup(io.write.addr, default, csrTable)
  val writeEn = io.write.op =/= CSR_NOP && io.write.op =/= CSR_R
  // External SEIP is visible to rd, but CSRRS/CSRRC must modify only the
  // software-pending bit (Privileged ISA 1.11, machine interrupt registers).
  val rmwData = Mux(io.write.addr === CSR_MIP,
                    Cat(csrData(31, 10), mipReal.seip, csrData(8, 0)),
                    csrData)
  val writeData = MuxLookup(io.write.op, 0.U)(Seq(
    CSR_W   -> io.write.data,
    CSR_RW  -> io.write.data,
    CSR_RS  -> (rmwData | io.write.data),
    CSR_RC  -> (rmwData & ~io.write.data),
  ))

  // CSR status signals
  val pending   = mip.asUInt & mie.asUInt
  val flagIntS  = pending & mideleg.asUInt
  val flagIntM  = pending & ~mideleg.asUInt
  val hasIntS   = Mux(mode < CSR_MODE_S ||
                          (mode === CSR_MODE_S && mstatus.sie),
                      flagIntS.orR, false.B)
  val hasIntM   = Mux(mode <= CSR_MODE_S || mstatus.mie,
                      flagIntM.orR, false.B)
  val hasInt    = hasIntM || hasIntS
  // Mem selects interrupts only at a precise instruction boundary.  Complete
  // xRET defensively even if an inconsistent record marks it as an interrupt.
  val isXret    = io.except.hasTrap &&
                  (io.except.isSret || io.except.isMret)
  val takeInt   = io.except.hasTrap && io.except.isInterrupt && !isXret
  val handIntS  = takeInt && !hasIntM
  val hasExc    = io.except.hasTrap && !takeInt && !isXret
  val hasExcS   = hasExc && medeleg.asUInt(io.except.excCause(4, 0))
  val handExcS  = !mode(1) && hasExcS
  // Apply delegation before priority selection for both trap targets.
  def interruptCause(flags: UInt): UInt = PriorityMux(Seq(
    EXC_M_EXT_INT, EXC_M_SOFT_INT, EXC_M_TIMER_INT,
    EXC_S_EXT_INT, EXC_S_SOFT_INT, EXC_S_TIMER_INT,
  ).map(cause => flags(cause.litValue.toInt) -> cause))
  val intCauseS = interruptCause(flagIntS)
  val intCauseM = interruptCause(flagIntM)
  val intCause  = Mux(handIntS, intCauseS, intCauseM)
  val cause     = Mux(takeInt, Cat(true.B, intCause),
                              Cat(false.B, io.except.excCause))
  val trapVecS  = Mux(stvec.mode(0) && takeInt,
                      (stvec.base + cause(29, 0)),
                      stvec.base) << 2
  val trapVecM  = Mux(mtvec.mode(0) && takeInt,
                      (mtvec.base + cause(29, 0)),
                      mtvec.base) << 2
  val trapVec   = Mux(handIntS || handExcS, trapVecS, trapVecM)

  // interrupt info
  mip.meip := mipReal.meip | io.irq.extern
  mip.seip := mipReal.seip | io.irq.supervisorExtern
  mip.mtip := mipReal.mtip | io.irq.timer
  mip.stip := mipReal.stip
  mip.msip := mipReal.msip | io.irq.soft
  mip.ssip := mipReal.ssip
  sip.seip := mip.seip && mideleg.data(9)
  sip.stip := mip.stip && mideleg.data(5)
  sip.ssip := mip.ssip && mideleg.data(1)

  // update current privilege mode
  val intMode   = Mux(handIntS, CSR_MODE_S, CSR_MODE_M)
  val sretMode  = Cat(false.B, sstatus.spp)
  val mretMode  = mstatus.mpp
  val excMode   = Mux(handExcS, CSR_MODE_S, CSR_MODE_M)
  val trapMode  = Mux(takeInt, intMode,
                  Mux(io.except.isSret, sretMode,
                  Mux(io.except.isMret, mretMode, excMode)))
  val nextMode  = Mux(io.except.hasTrap && !writeEn, trapMode, mode)
  mode := nextMode

  // update performance counters
  // Use the old inhibit and selector values at the committing edge. Explicit
  // half writes below override the entire 64-bit increment, including carry.
  when (!mcountinhibit.data(0)) {
    mcycle.data := mcycle.data + 1.U
  }
  when (io.write.retired && !mcountinhibit.data(2)) {
    minstret.data := minstret.data + 1.U
  }
  for (i <- 0 until hpmCount) {
    val selected = PerfEvent.groups.map { case (group, _) =>
      val events = io.perfEvents(group)
      // WARL validation checks the full local ID before it is stored.
      val hit = if (events.size == 1) events.head
                else events(mhpmevents(i).event(log2Ceil(events.size) - 1, 0))
      mhpmevents(i).group === group.id.U && hit
    }.reduce(_ || _)
    when (!mcountinhibit.data(i + 3) && selected) {
      mhpmcounters(i).data := mhpmcounters(i).data + 1.U
    }
  }

  // update CSRs
  when (writeEn) {
    // handle write operation
    // NOTE: must handle CSR write first in order to
    //       resolve RAW hazard before trap handling
    when (io.write.addr === CSR_SSTATUS) {
      mstatus.castAssign(SstatusCsr(), writeData)
    }
    when (io.write.addr === CSR_SIE) {
      mie.castAssign(SieCsr(),
        (mie.asUInt & ~mideleg.asUInt) | (writeData & mideleg.asUInt))
    }
    when (io.write.addr === CSR_SIP) {
      mipReal.castAssign(SipCsr(),
        (mipReal.asUInt & ~mideleg.asUInt) | (writeData & mideleg.asUInt))
    }
    when (io.write.addr === CSR_MCYCLE) {
      mcycle <= Cat(mcycle.data(63, 32), writeData)
    }
    when (io.write.addr === CSR_MINSTRET) {
      minstret <= Cat(minstret.data(63, 32), writeData)
    }
    when (io.write.addr === CSR_MCYCLEH) {
      mcycle <= Cat(writeData, mcycle.data(31, 0))
    }
    when (io.write.addr === CSR_MINSTRETH) {
      minstret <= Cat(writeData, minstret.data(31, 0))
    }
    for (i <- 0 until hpmCount) {
      when (io.write.addr === mhpmCounterAddr(i + 3)) {
        mhpmcounters(i) <= Cat(mhpmcounters(i).data(63, 32), writeData)
      }
      when (io.write.addr === mhpmCounterHighAddr(i + 3)) {
        mhpmcounters(i) <= Cat(writeData, mhpmcounters(i).data(31, 0))
      }
      when (io.write.addr === mhpmEventAddr(i + 3)) {
        mhpmevents(i) <= writeData
      }
    }
    when (io.write.addr === CSR_MCOUNTEREN) { mcounteren <= writeData }
    when (io.write.addr === CSR_SCOUNTEREN) { scounteren <= writeData }
    when (io.write.addr === CSR_MCOUNTINHIBIT) { mcountinhibit <= writeData }
    when (io.write.addr === CSR_STVEC)    { stvec <= writeData }
    when (io.write.addr === CSR_SSCRATCH) { sscratch <= writeData }
    when (io.write.addr === CSR_SEPC)     { sepc <= writeData }
    when (io.write.addr === CSR_SCAUSE)   { scause <= writeData }
    when (io.write.addr === CSR_STVAL)    { stval <= writeData }
    when (io.write.addr === CSR_SATP)     { satp <= writeData }
    when (io.write.addr === CSR_MSTATUS)  { mstatus <= writeData }
    when (io.write.addr === CSR_MEDELEG)  { medeleg <= writeData }
    when (io.write.addr === CSR_MIDELEG)  { mideleg <= writeData }
    when (io.write.addr === CSR_MIE)      { mie <= writeData }
    when (io.write.addr === CSR_MTVEC)    { mtvec <= writeData }
    when (io.write.addr === CSR_MSCRATCH) { mscratch <= writeData }
    when (io.write.addr === CSR_MEPC)     { mepc <= writeData }
    when (io.write.addr === CSR_MCAUSE)   { mcause <= writeData }
    when (io.write.addr === CSR_MTVAL)    { mtval <= writeData }
    when (io.write.addr === CSR_MIP)      { mipReal <= writeData }
  } .elsewhen (io.except.hasTrap) {
    // handle trap
    when (io.except.isSret) {
      // return from S-mode
      // update 'mstatus' because 'sstatus' is just wire
      mstatus.sie   := mstatus.spie
      mstatus.spie  := true.B
      mstatus.spp   := false.B
      mstatus.mprv  := false.B
    } .elsewhen (io.except.isMret) {
      // return from M-mode
      mstatus.mie   := mstatus.mpie
      mstatus.mpie  := true.B
      mstatus.mpp   := CSR_MODE_U
      when (mstatus.mpp =/= CSR_MODE_M) { mstatus.mprv := false.B }
    } .elsewhen (handIntS || handExcS) {
      // enter S-mode
      sepc          <= io.except.excPc
      scause        <= cause
      stval         <= io.except.excValue
      // update 'mstatus' because 'sstatus' is just wire
      mstatus.spie  := mstatus.sie
      mstatus.sie   := false.B
      mstatus.spp   := mode(0)
    } .otherwise {
      // enter M-mode
      mepc          <= io.except.excPc
      mcause        <= cause
      mtval         <= io.except.excValue
      mstatus.mpie  := mstatus.mie
      mstatus.mie   := false.B
      mstatus.mpp   := mode
    }
  }

  // read channel signals
  io.read.valid := valid
  io.read.data  := readData

  // CSR status signals
  io.hasInt   := hasInt
  io.busy     := writeEn
  io.mode     := mode
  io.sepc     := sepc.asUInt
  io.mepc     := mepc.asUInt
  io.trapVec  := trapVec

  // Same-cycle commit event for refetching under the new address space.
  // TLB invalidation remains the job of SFENCE.VMA.
  io.satpWritten := io.write.retired && writeEn && io.write.addr === CSR_SATP

  // paging signals
  val dataMode  = Mux(mstatus.mprv, mstatus.mpp, mode)
  io.pageEn     := !mode(1) && satp.mode
  io.dataPageEn := !dataMode(1) && satp.mode
  io.dataMode   := dataMode
  io.mxr        := mstatus.mxr
  io.basePpn    := satp.ppn
  io.sum        := mstatus.sum
}
