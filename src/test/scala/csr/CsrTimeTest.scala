package csr

import consts.CSR._
import utils.{CsrRegressionTester, TestDriver}

class CsrTimeTester(c: CsrFile) extends CsrRegressionTester(c) {
  resetCsr()
  // The shared RTC is neither the cycle counter nor affected by inhibit/CSR
  // writes. In particular, low-half rollover must reach TIMEH unchanged.
  write(CSR_MCOUNTINHIBIT, 0xffffffffL)
  write(CSR_MCYCLE, 0x1234)
  write(CSR_MCYCLEH, 0x5678)
  for (value <- Seq(BigInt(0), BigInt("ffffffff", 16),
                    BigInt("100000000", 16), BigInt("1234567887654321", 16))) {
    poke(c.io.rtcTime, value)
    step(2)
    read(CSR_TIME, value & 0xffffffffL)
    read(CSR_TIMEH, value >> 32)
    read(CSR_MCYCLE, 0x1234)
    read(CSR_MCYCLEH, 0x5678)
  }

  // A timer expiry first targets M mode. Firmware injects STIP independently
  // and may clear it while MTIP stays asserted, or retain it after MTIP drops.
  resetCsr()
  write(CSR_MIDELEG, 0x222)
  poke(c.io.irq.timer, true)
  poke(c.io.irq.soft, true)
  poke(c.io.irq.extern, true)
  read(CSR_MIP, 0x888)
  read(CSR_SIP, 0)
  write(CSR_MIP, 0x22)
  read(CSR_MIP, 0x8aa)
  read(CSR_SIP, 0x22)
  write(CSR_MIP, 0)
  read(CSR_MIP, 0x888)
  read(CSR_SIP, 0)
  poke(c.io.irq.supervisorExtern, true)
  read(CSR_MIP, 0xa88)
  read(CSR_SIP, 0x200)
  poke(c.io.irq.extern, false)
  poke(c.io.irq.timer, false)
  poke(c.io.irq.soft, false)
  read(CSR_MIP, 0x200)
  poke(c.io.irq.supervisorExtern, false)
  write(CSR_MIP, 0x22)
  read(CSR_MIP, 0x22)
}

object CsrTimeTest extends App {
  if (!TestDriver.execute(args, () => new CsrFile)(c => new CsrTimeTester(c))) sys.exit(1)
}
