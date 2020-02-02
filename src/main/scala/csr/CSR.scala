package csr

import chisel3._

import consts.Parameters._

abstract class CsrBundle extends Bundle {
  protected def requireWidth(data: UInt) =
      require(data.getWidth == getWidth,
              "Data width must be equal to CSR width.")

  def <=(data: UInt) = {
    requireWidth(data)
    this := data.asTypeOf(this)
  }
}

trait CsrObject[T <: CsrBundle] {
  def apply(): T
  def default(): T = 0.U.asTypeOf(apply())

  def apply[U <: Data](data: U): T = {
    val init = Wire(default())
    init <= data.asUInt
    init
  }
}

// supervisor status register
class SstatusCsr extends CsrBundle {
  val sd    = Bool()
  val wpri0 = UInt(11.W)
  val mxr   = Bool()
  val sum   = Bool()
  val wpri1 = Bool()
  val xs    = UInt(2.W)
  val fs    = UInt(2.W)
  val wpri2 = UInt(4.W)
  val spp   = Bool()
  val wpri3 = UInt(2.W)
  val spie  = Bool()
  val upie  = Bool()
  val wpri4 = UInt(2.W)
  val sie   = Bool()
  val uie   = Bool()

  override def <=(data: UInt) = {
    requireWidth(data)
    sum   := data(18)
    spp   := data(8)
    spie  := data(5)
    sie   := data(1)
  }
}

object SstatusCsr extends CsrObject[SstatusCsr] {
  def apply() = new SstatusCsr
}

// supervisor interrupt-enable register
class SieCsr extends CsrBundle {
  val wpri0 = UInt(22.W)
  val seie  = Bool()
  val ueie  = Bool()
  val wpri1 = UInt(2.W)
  val stie  = Bool()
  val utie  = Bool()
  val wpri2 = UInt(2.W)
  val ssie  = Bool()
  val usie  = Bool()

  override def <=(data: UInt) = {
    requireWidth(data)
    seie  := data(9)
    stie  := data(5)
    ssie  := data(1)
  }
}

object SieCsr extends CsrObject[SieCsr] {
  def apply() = new SieCsr
}

// supervisor interrupt-pending register
class SipCsr extends CsrBundle {
  val wpri0 = UInt(22.W)
  val seip  = Bool()
  val ueip  = Bool()
  val wpri1 = UInt(2.W)
  val stip  = Bool()
  val utip  = Bool()
  val wpri2 = UInt(2.W)
  val ssip  = Bool()
  val usip  = Bool()

  override def <=(data: UInt) = {
    requireWidth(data)
    ssip  := data(1)
  }
}

object SipCsr extends CsrObject[SipCsr] {
  def apply() = new SipCsr
}

// supervisor trap vector register
class StvecCsr extends CsrBundle {
  val base  = UInt(30.W)
  val mode  = UInt(2.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    base    := data(31, 2)
    mode(0) := data(0)
  }
}

object StvecCsr extends CsrObject[StvecCsr] {
  def apply() = new StvecCsr
}

// supervisor scratch register
class SscratchCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object SscratchCsr extends CsrObject[SscratchCsr] {
  def apply() = new SscratchCsr
}

// supervisor exception program counter register
class SepcCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object SepcCsr extends CsrObject[SepcCsr] {
  def apply() = new SepcCsr
}

// supervisor exception cause register
class ScauseCsr extends CsrBundle {
  val int   = Bool()
  val code  = UInt(31.W)
}

object ScauseCsr extends CsrObject[ScauseCsr] {
  def apply() = new ScauseCsr
}

// supervisor trap value register
class StvalCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object StvalCsr extends CsrObject[StvalCsr] {
  def apply() = new StvalCsr
}

// supervisor address translation and protection register
class SatpCsr extends CsrBundle {
  val mode  = Bool()
  val asid  = UInt(9.W)
  val ppn   = UInt(22.W)

  override def <=(data: UInt) {
    requireWidth(data)
    mode  := data(31)
    ppn   := data(21, 0)
  }
}

object SatpCsr extends CsrObject[SatpCsr] {
  def apply() = new SatpCsr
}

// machine status register
// supervisor interrupt-pending register
class MstatusCsr extends CsrBundle {
  val sd    = Bool()
  val wpri0 = UInt(8.W)
  val tsr   = Bool()
  val tw    = Bool()
  val tvm   = Bool()
  val mxr   = Bool()
  val sum   = Bool()
  val mprv  = Bool()
  val xs    = UInt(2.W)
  val fs    = UInt(2.W)
  val mpp   = UInt(2.W)
  val wpri1 = UInt(2.W)
  val spp   = Bool()
  val mpie  = Bool()
  val wpri2 = Bool()
  val spie  = Bool()
  val upie  = Bool()
  val mie   = Bool()
  val wpri3 = Bool()
  val sie   = Bool()
  val uie   = Bool()

  override def <=(data: UInt) = {
    requireWidth(data)
    sum   := data(18)
    mpp   := data(12, 11)
    spp   := data(8)
    mpie  := data(7)
    spie  := data(5)
    mie   := data(3)
    sie   := data(1)
  }
}

object MstatusCsr extends CsrObject[MstatusCsr] {
  def apply() = new MstatusCsr
}

// machine ISA register
class MisaCsr extends CsrBundle {
  val mxl   = UInt(2.W)
  val wlrl  = UInt(4.W)
  val ext   = UInt(26.W)

  override def <=(data: UInt) = {}
}

object MisaCsr extends CsrObject[MisaCsr] {
  def apply() = new MisaCsr
  override def default() = "h40141101".U.asTypeOf(apply())
}

// machine exception delegation register
class MedelegCsr extends CsrBundle {
  val data  = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    data(15)      := d(15)
    data(13, 12)  := d(13, 12)
    data(9, 8)    := d(9, 8)
    data(6)       := d(6)
    data(4, 2)    := d(4, 2)
    data(0)       := d(0)
  }
}

object MedelegCsr extends CsrObject[MedelegCsr] {
  def apply() = new MedelegCsr
}

// machine interrupt delegation register
class MidelegCsr extends CsrBundle {
  val data  = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    data(11)  := d(11)
    data(9)   := d(9)
    data(7)   := d(7)
    data(5)   := d(5)
    data(3)   := d(3)
    data(1)   := d(1)
  }
}

object MidelegCsr extends CsrObject[MidelegCsr] {
  def apply() = new MidelegCsr
}

// machine interrupt-enable register
class MieCsr extends CsrBundle {
  val wpri0 = UInt(20.W)
  val meie  = Bool()
  val wpri1 = Bool()
  val seie  = Bool()
  val ueie  = Bool()
  val mtie  = Bool()
  val wpri2 = Bool()
  val stie  = Bool()
  val utie  = Bool()
  val msie  = Bool()
  val wpri3 = Bool()
  val ssie  = Bool()
  val usie  = Bool()

  override def <=(data: UInt) = {
    requireWidth(data)
    meie  := data(11)
    seie  := data(9)
    mtie  := data(7)
    stie  := data(5)
    msie  := data(3)
    ssie  := data(1)
  }
}

object MieCsr extends CsrObject[MieCsr] {
  def apply() = new MieCsr
}

// machine interrupt-pending register
class MipCsr extends CsrBundle {
  val wpri0 = UInt(20.W)
  val meip  = Bool()
  val wpri1 = Bool()
  val seip  = Bool()
  val ueip  = Bool()
  val mtip  = Bool()
  val wpri2 = Bool()
  val stip  = Bool()
  val utip  = Bool()
  val msip  = Bool()
  val wpri3 = Bool()
  val ssip  = Bool()
  val usip  = Bool()

  override def <=(data: UInt) = {
    requireWidth(data)
    seip  := data(9)
    stip  := data(5)
    ssip  := data(1)
  }
}

object MipCsr extends CsrObject[MipCsr] {
  def apply() = new MipCsr
}

// machine trap vector register
class MtvecCsr extends CsrBundle {
  val base  = UInt(30.W)
  val mode  = UInt(2.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    base    := data(31, 2)
    mode(0) := data(0)
  }
}

object MtvecCsr extends CsrObject[MtvecCsr] {
  def apply() = new MtvecCsr
}

// machine scratch register
class MscratchCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object MscratchCsr extends CsrObject[MscratchCsr] {
  def apply() = new MscratchCsr
}

// machine exception program counter register
class MepcCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object MepcCsr extends CsrObject[MepcCsr] {
  def apply() = new MepcCsr
}

// machine exception cause register
class McauseCsr extends CsrBundle {
  val int   = Bool()
  val code  = UInt(31.W)
}

object McauseCsr extends CsrObject[McauseCsr] {
  def apply() = new McauseCsr
}

// machine trap value register
class MtvalCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object MtvalCsr extends CsrObject[MtvalCsr] {
  def apply() = new MtvalCsr
}

// machine cycle counter (64-bit)
class McycleCsr extends CsrBundle {
  val data  = UInt(64.W)
}

object McycleCsr extends CsrObject[McycleCsr] {
  def apply() = new McycleCsr
}

// machine instructions-retired counter (64-bit)
class MinstretCsr extends CsrBundle {
  val data  = UInt(64.W)
}

object MinstretCsr extends CsrObject[MinstretCsr] {
  def apply() = new MinstretCsr
}
