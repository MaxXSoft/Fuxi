package csr

import org.scalatest.funsuite.AnyFunSuite

import utils.DefaultTestConfig._
import configs.{CoreParamsKey, PlatformParamsKey}
import consts.CSR._
import utils.{CsrRegressionTester, TestDriver}

class CsrTrapVectorTester(c: CsrFile) extends CsrRegressionTester(c) {
  val wordMask = (BigInt(1) << 32) - 1
  // Distinct M/S bases, including carry out of the RV32 address space.
  val bases = Seq((BigInt(0x1000), BigInt(0x2000)),
                  (BigInt("ffffffd8", 16), BigInt("ffffffec", 16)))
  for ((baseM, baseS) <- bases; targetS <- Seq(false, true);
       vectored <- Seq(false, true); interrupt <- Seq(false, true)) {
    resetCsr()
    write(CSR_MTVEC, baseM | (if (vectored) 1 else 0))
    write(CSR_STVEC, baseS | (if (vectored) 1 else 0))
    write(CSR_MIDELEG, 0x222)
    write(CSR_MEDELEG, 1 << 13) // Delegate load page faults, not access faults.
    write(CSR_MIE, 0xaaa)
    write(CSR_MIP, 0x222) // All S sources pending: SEIP must win over SSIP/STIP.
    enterMode(CSR_MODE_S)
    write(CSR_SSTATUS, 2)
    poke(c.io.irq.extern, !targetS)
    expect(c.io.hasInt, true)
    val base = if (targetS) baseS else baseM
    val intCause = if (targetS) 9 else 11
    val excCause = if (targetS) 13 else 5
    val cause = if (interrupt) intCause else excCause
    val address = (base + (if (vectored && interrupt) 4 * intCause else 0)) & wordMask

    poke(c.io.except.hasTrap, true)
    poke(c.io.except.isInterrupt, interrupt)
    // The exception code must not affect an interrupt vector. Conversely,
    // pending interrupts must not add an offset to a synchronous trap.
    for (rawCause <- (if (interrupt) Seq(0, 5, 13, 0x3fffffff) else Seq(excCause))) {
      poke(c.io.except.excCause, rawCause)
      expect(c.io.trapVec, address)
    }
    step(1)
    poke(c.io.except.hasTrap, false)
    poke(c.io.except.isInterrupt, false)
    expect(c.io.mode, if (targetS) CSR_MODE_S else CSR_MODE_M)
    read(if (targetS) CSR_SCAUSE else CSR_MCAUSE,
         BigInt(cause) | (if (interrupt) BigInt(1) << 31 else BigInt(0)))
    read(if (targetS) CSR_SEPC else CSR_MEPC, 0x1000)
  }
}

class CsrTrapVectorTest extends AnyFunSuite {
  test("select direct and vectored trap targets with pending interrupts and address wraparound") {
    TestDriver.run(new CsrFile(p(CoreParamsKey).hpmCounters, p(PlatformParamsKey).hartId)) { c =>
      new CsrTrapVectorTester(c)
    }
  }
}
