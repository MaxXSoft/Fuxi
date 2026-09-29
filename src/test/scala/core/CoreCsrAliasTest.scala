package core

import utils.InstEncoding._
import chisel3._
import consts.CSR._
import utils.{CoreProgram, CoreProgramTester, CoreWrapper, TestDriver}

object CsrAliasProgram extends CoreProgram {
  def write(addr: UInt, value: Int): Unit = {
    emit32(addi(5, 0, value)) // addi t0, zero, value
    emit32(csrw(addr.litValue.toInt, 5)) // csrw addr, t0
  }
  def read(addr: UInt, value: Int): Unit = {
    expectWriteback(6, value)
    emit32(csrr(6, addr.litValue.toInt)) // csrrs t1, addr, zero
  }
  // Check both directions of every writable supervisor alias.
  write(CSR_MSTATUS, 2); read(CSR_SSTATUS, 2)
  write(CSR_SSTATUS, 0); read(CSR_MSTATUS, 0)
  write(CSR_MIDELEG, 0x222)
  write(CSR_MIE, 0x222); read(CSR_SIE, 0x222)
  write(CSR_SIE, 0); read(CSR_MIE, 0)
  write(CSR_MIP, 2); read(CSR_SIP, 2)
  write(CSR_SIP, 0); read(CSR_MIP, 0)
  // SIE/SIP reads also depend on a preceding delegation write.
  write(CSR_MIE, 0x222)
  write(CSR_MIP, 0x222)
  write(CSR_MIDELEG, 0); read(CSR_SIE, 0)
  write(CSR_MIDELEG, 0x222); read(CSR_SIP, 0x222)
  // High-half aliases are stable over the short test, unlike cycle's low half.
  write(CSR_MCYCLEH, 2); read(CSR_CYCLEH, 2)
  write(CSR_MINSTRETH, 3); read(CSR_INSTRETH, 3)
  val donePc = finish()
}

object CoreCsrAliasTest extends App {
  if (!TestDriver.execute(args, () => new CoreWrapper(CsrAliasProgram.words))(
    c => new CoreProgramTester(c, CsrAliasProgram, CsrAliasProgram.donePc, 600))) sys.exit(1)
}
