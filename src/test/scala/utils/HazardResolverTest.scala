package utils

import chisel3._
import consts.CSR._
import consts.CsrOp._

class HazardResolverTester(c: HazardResolver) extends PeekPokeTester(c) {
  for (port <- Seq(c.io.regRead1, c.io.regRead2)) {
    poke(port.en, false)
    poke(port.addr, 0)
  }
  for (port <- Seq(c.io.aluReg, c.io.memReg, c.io.wbReg)) {
    poke(port.en, false)
    poke(port.addr, 0)
    poke(port.data, 0)
    poke(port.load, false)
  }
  poke(c.io.rf1.data, 0)
  poke(c.io.rf2.data, 0)
  poke(c.io.check.addr, 0)
  poke(c.io.excMon.valid, false)
  poke(c.io.wbExcMon.addr, 0)
  poke(c.io.wbExcMon.set, false)
  poke(c.io.wbExcMon.clear, false)
  poke(c.io.wbExcMon.clearAll, false)
  poke(c.io.csr.valid, true)
  poke(c.io.csr.data, 0)
  poke(c.io.csrRead.op, CSR_R)
  poke(c.io.csrRead.addr, CSR_MSTATUS)
  for (port <- Seq(c.io.memCsr, c.io.wbCsr)) {
    poke(port.op, CSR_NOP)
    poke(port.addr, 0)
    poke(port.data, 0)
    poke(port.retired, false)
  }
  def read(addr: UInt, hazard: Boolean): Unit = {
    poke(c.io.csrRead.addr, addr)
    expect(c.io.csrFlag, hazard)
  }
  for (port <- Seq(c.io.memCsr, c.io.wbCsr)) {
    poke(port.op, CSR_W)
    for (index <- Seq(0, 2, 3, 10, 31)) {
      val writes = Seq(0xb00 + index, 0xb80 + index)
      val reads = Seq(0xb00 + index, 0xb80 + index, 0xc00 + index, 0xc80 + index)
      for (write <- writes; snapshot <- reads) {
        poke(port.addr, write)
        read(snapshot.U(12.W), true)
        read(CSR_MSTATUS, false)
        read((if (index == 3) mhpmCounterAddr(4) else mhpmCounterAddr(3)), false)
      }
    }
    for (control <- Seq(CSR_MCOUNTEREN, CSR_SCOUNTEREN)) {
      poke(port.addr, control)
      for (alias <- Seq(CSR_CYCLE, CSR_INSTRETH, hpmCounterAddr(3), hpmCounterHighAddr(31))) {
        read(alias, true)
      }
      read(CSR_MCYCLE, false)
      read(mhpmCounterAddr(3), false)
      // Unimplemented TIME and nearby CSR ranges are not counter aliases.
      for (address <- Seq(0xc01, 0xc81, 0xc20, 0xca0, 0xcff))
        read(address.U(12.W), false)
    }
    poke(port.addr, CSR_MCOUNTINHIBIT)
    for (snapshot <- Seq(CSR_MCYCLE, CSR_CYCLEH, CSR_MINSTRET, CSR_INSTRETH,
                         mhpmCounterAddr(3), hpmCounterHighAddr(31))) {
      read(snapshot, true)
    }
    read(CSR_MSTATUS, false)
    for (index <- Seq(3, 10, 31)) {
      poke(port.addr, mhpmEventAddr(index))
      read(mhpmCounterAddr(index), true)
      read(hpmCounterHighAddr(index), true)
      read(CSR_MCYCLE, false)
    }
    poke(port.addr, mhpmCounterAddr(3))
    poke(c.io.csrRead.addr, hpmCounterHighAddr(3))
    for (op <- Seq(CSR_NOP, CSR_W)) {
      poke(c.io.csrRead.op, op)
      expect(c.io.csrFlag, false)
    }
    poke(c.io.csrRead.op, CSR_R)
    // Read-only or absent preceding CSR operations do not create write hazards.
    for (op <- Seq(CSR_NOP, CSR_R)) {
      poke(port.op, op)
      expect(c.io.csrFlag, false)
    }
    poke(port.op, CSR_NOP)
    poke(port.retired, true)
    for (snapshot <- Seq(CSR_MINSTRET, CSR_INSTRETH, mhpmCounterAddr(3), hpmCounterHighAddr(31))) {
      read(snapshot, true)
    }
    read(CSR_CYCLE, false)
    read(mhpmEventAddr(3), false)
    poke(port.retired, false)
    read(hpmCounterAddr(3), false)
  }
}

object HazardResolverTest extends App {
  if (!TestDriver.execute(args, () => new HazardResolver)(c => new HazardResolverTester(c))) sys.exit(1)
}
