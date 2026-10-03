package csr

import chisel3._
import chisel3.util.Cat

import consts.PerfEvent
import consts.Parameters._

abstract class CsrBundle extends Bundle {
  protected def requireWidth(data: UInt) =
      require(data.getWidth == getWidth,
              "Data width must be equal to CSR width.")

  def <=(data: UInt) = {
    requireWidth(data)
    this := data.asTypeOf(this)
  }

  def castAssign[T <: CsrBundle](that: T, data: UInt) = {
    val temp = WireDefault(asTypeOf(that))
    temp <= data
    this <= temp.asUInt
  }
}

abstract class CsrObject[T <: CsrBundle](mk: => T) {
  def apply(): T = mk
  def default() = 0.U.asTypeOf(apply())

  def apply[U <: Data](data: U): T = {
    val init = WireDefault(default())
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
    mxr   := data(19)
    sum   := data(18)
    spp   := data(8)
    spie  := data(5)
    sie   := data(1)
  }
}

object SstatusCsr extends CsrObject(new SstatusCsr)

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

object SieCsr extends CsrObject(new SieCsr)

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

object SipCsr extends CsrObject(new SipCsr)

// supervisor trap vector register
class StvecCsr extends CsrBundle {
  val base  = UInt(30.W)
  val mode  = UInt(2.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    base  := data(31, 2)
    mode  := data(0)
  }
}

object StvecCsr extends CsrObject(new StvecCsr)

// supervisor scratch register
class SscratchCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object SscratchCsr extends CsrObject(new SscratchCsr)

// supervisor exception program counter register
class SepcCsr extends CsrBundle {
  val data  = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    data  := Cat(d(31, 1), 0.U(1.W))
  }
}

object SepcCsr extends CsrObject(new SepcCsr)

// supervisor exception cause register
class ScauseCsr extends CsrBundle {
  val int   = Bool()
  val code  = UInt(31.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    int   := data(31)
    code  := data(3, 0)
  }
}

object ScauseCsr extends CsrObject(new ScauseCsr)

// supervisor trap value register
class StvalCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object StvalCsr extends CsrObject(new StvalCsr)

// supervisor address translation and protection register
class SatpCsr extends CsrBundle {
  val mode  = Bool()
  val asid  = UInt(9.W)
  val ppn   = UInt(22.W)

  override def <=(data: UInt): Unit = {
    requireWidth(data)
    mode  := data(31)
    ppn   := data(21, 0)
  }
}

object SatpCsr extends CsrObject(new SatpCsr)

// machine status register
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
    mxr   := data(19)
    sum   := data(18)
    mprv  := data(17)
    mpp   := Mux(data(12, 11) === 2.U, 0.U, data(12, 11))
    spp   := data(8)
    mpie  := data(7)
    spie  := data(5)
    mie   := data(3)
    sie   := data(1)
  }
}

object MstatusCsr extends CsrObject(new MstatusCsr)

// machine ISA register
class MisaCsr extends CsrBundle {
  val mxl   = UInt(2.W)
  val wlrl  = UInt(4.W)
  val ext   = UInt(26.W)

  override def <=(data: UInt) = {}
}

object MisaCsr extends CsrObject(new MisaCsr) {
  override def default() = "h40141105".U.asTypeOf(apply())
}

// machine exception delegation register
class MedelegCsr extends CsrBundle {
  val data  = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    data  := Cat(d(15), 0.U(1.W), d(13, 12), 0.U(2.W), d(9, 8),
                 0.U(1.W), d(6), 0.U(1.W), d(4, 2), 0.U(1.W), d(0))
  }
}

object MedelegCsr extends CsrObject(new MedelegCsr)

// machine interrupt delegation register
class MidelegCsr extends CsrBundle {
  val data  = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    data  := Cat(0.U(2.W), d(9), 0.U(3.W), d(5), 0.U(3.W), d(1), 0.U(1.W))
  }
}

object MidelegCsr extends CsrObject(new MidelegCsr)

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

object MieCsr extends CsrObject(new MieCsr)

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

object MipCsr extends CsrObject(new MipCsr)

// machine trap vector register
class MtvecCsr extends CsrBundle {
  val base  = UInt(30.W)
  val mode  = UInt(2.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    base  := data(31, 2)
    mode  := data(0)
  }
}

object MtvecCsr extends CsrObject(new MtvecCsr)

// machine scratch register
class MscratchCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object MscratchCsr extends CsrObject(new MscratchCsr)

// machine exception program counter register
class MepcCsr extends CsrBundle {
  val data  = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    data  := Cat(d(31, 1), 0.U(1.W))
  }
}

object MepcCsr extends CsrObject(new MepcCsr)

// machine exception cause register
class McauseCsr extends CsrBundle {
  val int   = Bool()
  val code  = UInt(31.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    int   := data(31)
    code  := data(3, 0)
  }
}

object McauseCsr extends CsrObject(new McauseCsr)

// machine trap value register
class MtvalCsr extends CsrBundle {
  val data  = UInt(32.W)
}

object MtvalCsr extends CsrObject(new MtvalCsr)

// machine cycle counter (64-bit)
class McycleCsr extends CsrBundle {
  val data  = UInt(64.W)
}

object McycleCsr extends CsrObject(new McycleCsr)

// machine instructions-retired counter (64-bit)
class MinstretCsr extends CsrBundle {
  val data  = UInt(64.W)
}

object MinstretCsr extends CsrObject(new MinstretCsr)

// machine hardware performance counter (64-bit)
class MhpmcounterCsr extends CsrBundle {
  val data = UInt(64.W)
}

object MhpmcounterCsr extends CsrObject(new MhpmcounterCsr)

// machine hardware performance event selector
class MhpmeventCsr extends CsrBundle {
  val group = UInt(PerfEvent.GROUP_WIDTH.W)
  val event = UInt(PerfEvent.EVENT_WIDTH.W)

  override def <=(data: UInt) = {
    requireWidth(data)
    val nextGroup = data(31, PerfEvent.EVENT_WIDTH)
    val nextEvent = data(PerfEvent.EVENT_WIDTH - 1, 0)
    val valid = PerfEvent.groups.map { case (g, events) =>
      nextGroup === g.id.U && nextEvent < events.values.size.U
    }.reduce(_ || _)
    // Reject the whole encoding: an invalid local ID must not alias another
    // event, and group zero always canonicalizes to the disabled selector.
    group := Mux(valid, nextGroup, 0.U)
    event := Mux(valid, nextEvent, 0.U)
  }
}

object MhpmeventCsr extends CsrObject(new MhpmeventCsr)

// TIME access can be enabled, but the hart cannot inhibit the platform clock.
// Absent HPM counter bits are WARL zero in all three control registers.
abstract class CounterMaskCsr(hpmCount: Int, includeTime: Boolean) extends CsrBundle {
  require(hpmCount >= 0 && hpmCount <= 29)
  val data = UInt(32.W)

  override def <=(d: UInt) = {
    requireWidth(d)
    val counterMask = (BigInt(1) << (hpmCount + 3)) - 1
    val mask = if (includeTime) counterMask else counterMask & ~BigInt(2)
    data := d & mask.U(32.W)
  }
}

// Counter mask CSRs take hpmCount at each call, so they cannot extend CsrObject.
abstract class CounterMaskCsrObject[T <: CounterMaskCsr](mk: Int => T) {
  def apply(hpmCount: Int): T = mk(hpmCount)
  def default(hpmCount: Int): T = 0.U.asTypeOf(apply(hpmCount))
}

// machine counter enable register
class McounterenCsr(hpmCount: Int) extends CounterMaskCsr(hpmCount, includeTime = true)

object McounterenCsr extends CounterMaskCsrObject(new McounterenCsr(_))

// supervisor counter enable register
class ScounterenCsr(hpmCount: Int) extends CounterMaskCsr(hpmCount, includeTime = true)

object ScounterenCsr extends CounterMaskCsrObject(new ScounterenCsr(_))

// machine counter inhibit register
class McountinhibitCsr(hpmCount: Int) extends CounterMaskCsr(hpmCount, includeTime = false)

object McountinhibitCsr extends CounterMaskCsrObject(new McountinhibitCsr(_))
