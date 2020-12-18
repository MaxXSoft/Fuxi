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

class CsrFile extends Module {
  val io = IO(new Bundle {
    // read channel
    val read    = Flipped(new CsrReadIO)
    // write channel
    val write   = Flipped(new CsrWriteIO)
    // interrupt request
    val irq     = new InterruptIO
    // exception information
    val except  = Input(new ExceptInfoIO)
    // CSR status
    val hasInt  = Output(Bool())
    val busy    = Output(Bool())
    val mode    = Output(UInt(CSR_MODE_WIDTH.W))
    val sepc    = Output(UInt(ADDR_WIDTH.W))
    val mepc    = Output(UInt(ADDR_WIDTH.W))
    val trapVec = Output(UInt(ADDR_WIDTH.W))
    // paging signals
    val pageEn  = Output(Bool())
    val basePpn = Output(UInt(PPN_WIDTH.W))
    val sum     = Output(Bool())
  })

  // current privilege mode
  val mode      = RegInit(CSR_MODE_M)

  // definition of CSRs
  val mstatus   = RegInit(MstatusCsr.default)
  val misa      = MisaCsr.default
  val medeleg   = RegInit(MedelegCsr.default)
  val mideleg   = RegInit(MidelegCsr.default)
  val mie       = RegInit(MieCsr.default)
  val mtvec     = RegInit(MtvecCsr.default)
  val mscratch  = RegInit(MscratchCsr.default)
  val mepc      = RegInit(MepcCsr.default)
  val mcause    = RegInit(McauseCsr.default)
  val mtval     = RegInit(MtvalCsr.default)
  val mipReal   = RegInit(MipCsr.default)
  val mip       = MipCsr.default
  val mcycle    = RegInit(McycleCsr.default)
  val minstret  = RegInit(MinstretCsr.default)
  val sstatus   = SstatusCsr(mstatus)
  val sie       = SieCsr(mie)
  val sip       = SipCsr(mip)
  val stvec     = RegInit(StvecCsr.default)
  val sscratch  = RegInit(SscratchCsr.default)
  val sepc      = RegInit(SepcCsr.default)
  val scause    = RegInit(ScauseCsr.default)
  val stval     = RegInit(StvalCsr.default)
  val satp      = RegInit(SatpCsr.default)
  val cycle     = mcycle.asUInt
  val instret   = minstret.asUInt

  // all supported CSRs
  val default  =
  //                                                   writable
  //                                       csrData readable|
  //                                          |         |  |
                                  List(0.U,             N, N)
  var csrTable = Array(
    BitPat(CSR_CYCLE)         ->  List(cycle(31, 0),    Y, N),
    BitPat(CSR_INSTRET)       ->  List(instret(31, 0),  Y, N),
    BitPat(CSR_CYCLEH)        ->  List(cycle(63, 32),   Y, N),
    BitPat(CSR_INSTRETH)      ->  List(instret(63, 32), Y, N),
    BitPat(CSR_SSTATUS)       ->  List(sstatus.asUInt,  Y, Y),
    BitPat(CSR_SIE)           ->  List(sie.asUInt,      Y, Y),
    BitPat(CSR_STVEC)         ->  List(stvec.asUInt,    Y, Y),
    BitPat(CSR_SCOUNTEREN)    ->  List(0.U,             Y, Y),
    BitPat(CSR_SSCRATCH)      ->  List(sscratch.asUInt, Y, Y),
    BitPat(CSR_SEPC)          ->  List(sepc.asUInt,     Y, Y),
    BitPat(CSR_SCAUSE)        ->  List(scause.asUInt,   Y, Y),
    BitPat(CSR_STVAL)         ->  List(stval.asUInt,    Y, Y),
    BitPat(CSR_SIP)           ->  List(sip.asUInt,      Y, Y),
    BitPat(CSR_SATP)          ->  List(satp.asUInt,     Y, Y),
    BitPat(CSR_MVENDERID)     ->  List(0.U,             Y, N),
    BitPat(CSR_MARCHID)       ->  List(0.U,             Y, N),
    BitPat(CSR_MIMPID)        ->  List(0.U,             Y, N),
    BitPat(CSR_MHARTID)       ->  List(0.U,             Y, N),
    BitPat(CSR_MSTATUS)       ->  List(mstatus.asUInt,  Y, Y),
    BitPat(CSR_MISA)          ->  List(misa.asUInt,     Y, Y),
    BitPat(CSR_MEDELEG)       ->  List(medeleg.asUInt,  Y, Y),
    BitPat(CSR_MIDELEG)       ->  List(mideleg.asUInt,  Y, Y),
    BitPat(CSR_MIE)           ->  List(mie.asUInt,      Y, Y),
    BitPat(CSR_MTVEC)         ->  List(mtvec.asUInt,    Y, Y),
    BitPat(CSR_MCOUNTEREN)    ->  List(0.U,             Y, Y),
    BitPat(CSR_MSCRATCH)      ->  List(mscratch.asUInt, Y, Y),
    BitPat(CSR_MEPC)          ->  List(mepc.asUInt,     Y, Y),
    BitPat(CSR_MCAUSE)        ->  List(mcause.asUInt,   Y, Y),
    BitPat(CSR_MTVAL)         ->  List(mtval.asUInt,    Y, Y),
    BitPat(CSR_MIP)           ->  List(mip.asUInt,      Y, Y),
    BitPat(CSR_PMPCFG0)       ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPCFG1)       ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPCFG2)       ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPCFG3)       ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR0)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR1)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR2)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR3)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR4)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR5)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR6)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR7)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR8)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR9)      ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR10)     ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR11)     ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR12)     ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR13)     ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR14)     ->  List(0.U,             Y, Y),
    BitPat(CSR_PMPADDR15)     ->  List(0.U,             Y, Y),
    BitPat(CSR_MCYCLE)        ->  List(cycle(31, 0),    Y, Y),
    BitPat(CSR_MINSTRET)      ->  List(instret(31, 0),  Y, Y),
    BitPat(CSR_MCYCLEH)       ->  List(cycle(63, 32),   Y, Y),
    BitPat(CSR_MINSTRETH)     ->  List(instret(63, 32), Y, Y),
    BitPat(CSR_MCOUNTINHIBIT) ->  List(0.U,             Y, Y),
  )

  // CSR read related signals
  val readData :: (readable: Bool) :: (writable: Bool) :: Nil =
      ListLookup(io.read.addr, default, csrTable)
  val readValid = MuxLookup(io.read.op, false.B, Seq(
    CSR_R   -> readable,
    CSR_W   -> writable,
    CSR_RW  -> (readable && writable),
    CSR_RS  -> (readable && writable),
    CSR_RC  -> (readable && writable),
  ))
  val modeValid = io.read.addr(9, 8) <= mode
  val valid     = readValid && modeValid

  // CSR write related signals
  val csrData :: _ :: _ :: Nil =
      ListLookup(io.write.addr, default, csrTable)
  val writeEn   = io.write.op =/= CSR_NOP && io.write.op =/= CSR_R
  val writeData = MuxLookup(io.write.op, 0.U, Seq(
    CSR_W   -> io.write.data,
    CSR_RW  -> io.write.data,
    CSR_RS  -> (csrData | io.write.data),
    CSR_RC  -> (csrData & ~io.write.data),
  ))

  // CSR status signals
  val flagIntS  = sip.asUInt & sie.asUInt
  val flagIntM  = mip.asUInt & mie.asUInt
  val hasIntS   = Mux(mode < CSR_MODE_S ||
                          (mode === CSR_MODE_S && mstatus.sie),
                      (flagIntS & mideleg.asUInt).orR, false.B)
  val hasIntM   = Mux(mode <= CSR_MODE_S || mstatus.mie,
                      (flagIntM & ~mideleg.asUInt).orR, false.B)
  val hasInt    = hasIntM || hasIntS
  val handIntS  = hasInt && !hasIntM
  val hasExc    = io.except.hasTrap && !hasInt
  val hasExcS   = hasExc && medeleg.asUInt()(io.except.excCause(4, 0))
  val handExcS  = !mode(1) && hasExcS
  val intCauseS = Mux(flagIntS(EXC_S_EXT_INT), EXC_S_EXT_INT,
                  Mux(flagIntS(EXC_S_SOFT_INT), EXC_S_SOFT_INT,
                                                EXC_S_TIMER_INT))
  val intCauseM = Mux(flagIntM(EXC_M_EXT_INT), EXC_M_EXT_INT,
                  Mux(flagIntM(EXC_M_SOFT_INT), EXC_M_SOFT_INT,
                  Mux(flagIntM(EXC_M_TIMER_INT), EXC_M_TIMER_INT,
                                                 intCauseS)))
  val intCause  = Mux(handIntS, intCauseS, intCauseM)
  val cause     = Mux(hasInt, Cat(true.B, intCause),
                              Cat(false.B, io.except.excCause))
  val trapVecS  = Mux(stvec.mode(0) && hasInt, (stvec.base + cause(29, 0)),
                      stvec.base) << 2
  val trapVecM  = Mux(mtvec.mode(0) && hasInt, (mtvec.base + cause(29, 0)),
                      mtvec.base) << 2
  val trapVec   = Mux(handIntS || handExcS, trapVecS, trapVecM)

  // interrupt info
  mip.meip := mipReal.meip | io.irq.extern
  mip.seip := mipReal.seip | io.irq.extern
  mip.mtip := mipReal.mtip | io.irq.timer
  mip.stip := mipReal.stip | io.irq.timer
  mip.msip := mipReal.msip | io.irq.soft
  mip.ssip := mipReal.ssip | io.irq.soft

  // update current privilege mode
  val intMode   = Mux(handIntS, CSR_MODE_S, CSR_MODE_M)
  val sretMode  = Cat(false.B, sstatus.spp)
  val mretMode  = mstatus.mpp
  val excMode   = Mux(handExcS, CSR_MODE_S, CSR_MODE_M)
  val trapMode  = Mux(hasInt, intMode,
                  Mux(io.except.isSret, sretMode,
                  Mux(io.except.isMret, mretMode, excMode)))
  val nextMode  = Mux(io.except.hasTrap && !writeEn, trapMode, mode)
  mode := nextMode

  // update performance counters
  mcycle.data := mcycle.data + 1.U
  when (io.write.retired) {
    minstret.data := minstret.data + 1.U
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
      mie.castAssign(SieCsr(), writeData)
    }
    when (io.write.addr === CSR_SIP) {
      mipReal.castAssign(SipCsr(), writeData)
    }
    when (io.write.addr === CSR_MCYCLE) {
      mcycle.data := Cat(mcycle.data(63, 32), writeData)
    }
    when (io.write.addr === CSR_MINSTRET) {
      minstret.data := Cat(minstret.data(63, 32), writeData)
    }
    when (io.write.addr === CSR_MCYCLEH) {
      mcycle.data := Cat(writeData, mcycle.data(31, 0))
    }
    when (io.write.addr === CSR_MINSTRETH) {
      minstret.data := Cat(writeData, minstret.data(31, 0))
    }
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
    } .elsewhen (io.except.isMret) {
      // return from M-mode
      mstatus.mie   := mstatus.mpie
      mstatus.mpie  := true.B
      mstatus.mpp   := CSR_MODE_U
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

  // paging signals
  io.pageEn   := !mode(1) && satp.mode
  io.basePpn  := satp.ppn
  io.sum      := mstatus.sum
}
