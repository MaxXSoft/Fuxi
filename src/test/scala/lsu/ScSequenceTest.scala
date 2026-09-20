package lsu

import chisel3._
import chisel3.util._
import core._
import io._
import consts.Parameters._
import utils.{PeekPokeTester, TestDriver}

class ScSequenceHarness(gap: Int, interveningSc: Boolean) extends Module {
  val io = IO(new DebugIO)
  val core = Module(new Core)
  val ram = Module(new sim.RAM)
  def addi(rd: Int, rs: Int, imm: Int): BigInt = BigInt(((imm & 4095) << 20) | (rs << 15) | (rd << 7) | 0x13)
  def sw(rs: Int, base: Int): BigInt = BigInt((rs << 20) | (base << 15) | (2 << 12) | 0x23)
  def lr(rd: Int, base: Int): BigInt = BigInt((2 << 27) | (base << 15) | (2 << 12) | (rd << 7) | 0x2f)
  def sc(rd: Int, rs: Int, base: Int): BigInt = BigInt((3 << 27) | (rs << 20) | (base << 15) | (2 << 12) | (rd << 7) | 0x2f)
  val code = Seq(addi(1, 0, 0x100), addi(2, 0, 0x104), addi(3, 0, 7),
    sw(0, 1), sw(0, 2), lr(4, 1),
    if (interveningSc) sc(5, 3, 2) else sw(3, 2)) ++ Seq.fill(gap)(addi(0, 0, 0)) ++ Seq(
    sc(6, 3, 1), BigInt(0x0000a383), BigInt(0x0000006f))
  val instructions = VecInit((code ++ Seq.fill(32 - code.size)(BigInt(0x13))).map(_.U(32.W)))
  core.io.rom.rdata := RegNext(instructions((core.io.rom.addr - RESET_PC)(6, 2)), 0x13.U)
  core.io.rom.valid := true.B
  core.io.rom.fault := false.B
  core.io.rom.accessFault := false.B
  core.io.ram <> ram.io
  core.io.irq.timer := false.B
  core.io.irq.soft := false.B
  core.io.irq.extern := false.B
  core.io.cache.flushDataDone := true.B
  core.io.cache.flushDataAccessFault := false.B
  io <> core.io.debug
}

object ScSequenceTest extends App {
  // Adjacent instructions exercise WB forwarding; gaps exercise committed
  // reservation state. An unrelated ordinary store preserves the reservation.
  for (gap <- Seq(0, 1, 2); interveningSc <- Seq(true, false)) {
    if (!TestDriver.execute(args, () => new ScSequenceHarness(gap, interveningSc)) {
      c => new PeekPokeTester(c) {
        var seenSecondSc = false
        var seenLoad = false
        for (_ <- 0 until 60) {
          step(1)
          if (peek(c.io.regWen) != 0) {
            if (peek(c.io.regWaddr) == 5) expect(c.io.regWdata, 1)
            if (peek(c.io.regWaddr) == 6) {
              seenSecondSc = true
              expect(c.io.regWdata, if (interveningSc) 1 else 0)
            }
            if (peek(c.io.regWaddr) == 7) {
              seenLoad = true
              expect(c.io.regWdata, if (interveningSc) 0 else 7)
            }
          }
        }
        assert(seenSecondSc && seenLoad, "SC sequence did not finish")
      }
    }) sys.exit(1)
  }
}
