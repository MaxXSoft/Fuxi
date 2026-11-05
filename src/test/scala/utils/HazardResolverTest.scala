package utils

import org.scalatest.funsuite.AnyFunSuite

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

  // Both operand ports resolve their candidate independently of read.en.
  // Exercise every producer enable/match combination, x0, and ALU > MEM > WB
  // priority. A disabled operand must never create a load-use interlock.
  val producers = Seq(c.io.aluReg, c.io.memReg, c.io.wbReg)
  for ((read, rf) <- Seq((c.io.regRead1, c.io.rf1), (c.io.regRead2, c.io.rf2))) {
    for (address <- Seq(0, 7, 31); enabled <- 0 until 8; matches <- 0 until 8) {
      poke(read.addr, address)
      poke(rf.data, 0x12345678)
      for ((port, i) <- producers.zipWithIndex) {
        poke(port.en, (enabled & (1 << i)) != 0)
        poke(port.addr, if ((matches & (1 << i)) != 0) address else 21)
        poke(port.data, 0x76543210L + i)
        poke(port.load, i < 2)
      }
      val selected = (0 until 3).find(i => ((enabled & matches) & (1 << i)) != 0)
      val expected = if (address == 0) 0L else selected.map(0x76543210L + _).getOrElse(0x12345678L)
      for (consumed <- Seq(false, true)) {
        poke(read.en, consumed)
        expect(rf.en, true)
        expect(rf.addr, address)
        expect(read.data, expected)
        expect(c.io.loadFlag, consumed && (matches & 3) != 0)
      }
    }
    poke(read.en, false)
  }
  producers.foreach { port => poke(port.en, false); poke(port.load, false) }

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
      for (alias <- Seq(CSR_CYCLE, CSR_TIME, CSR_TIMEH, CSR_INSTRETH,
                        hpmCounterAddr(3), hpmCounterHighAddr(31))) {
        read(alias, true)
      }
      read(CSR_MCYCLE, false)
      read(mhpmCounterAddr(3), false)
      // Nearby CSR ranges are not counter aliases.
      for (address <- Seq(0xc20, 0xca0, 0xcff))
        read(address.U(12.W), false)
    }
    poke(port.addr, CSR_MCOUNTINHIBIT)
    for (snapshot <- Seq(CSR_MCYCLE, CSR_CYCLEH, CSR_MINSTRET, CSR_INSTRETH,
                         mhpmCounterAddr(3), hpmCounterHighAddr(31))) {
      read(snapshot, true)
    }
    read(CSR_MSTATUS, false)
    read(CSR_TIME, false)
    read(CSR_TIMEH, false)
    // There is no writable machine TIME alias at counter slot 1.
    for (address <- Seq(0xb01, 0xb81)) {
      poke(port.addr, address)
      read(CSR_TIME, false)
      read(CSR_TIMEH, false)
    }
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
    read(CSR_TIME, false)
    read(CSR_TIMEH, false)
    read(mhpmEventAddr(3), false)
    poke(port.retired, false)
    read(hpmCounterAddr(3), false)
  }
}

class HazardResolverTest extends AnyFunSuite {
  test("resolve pipeline hazards and forwarding") {
    TestDriver.run(new HazardResolver) { c =>
      new HazardResolverTester(c)
    }
  }
}
