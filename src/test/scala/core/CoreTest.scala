package core

import chisel3._
import chisel3.iotesters.{Driver, PeekPokeTester}
import scala.io.Source

import io._
import sim._
import utils.ArgParser

class CoreWrapper(initFile: String) extends Module {
  val io    = IO(new DebugIO)
  val core  = Module(new Core)
  val rom   = Module(new ROM(initFile))
  val ram   = Module(new RAM)

  core.io.rom   <> rom.io
  core.io.ram   <> ram.io
  core.io.debug <> io
}

class CoreUnitTester(c: CoreWrapper, traceFile: String)
      extends PeekPokeTester(c) {
  def runTrace(source: Source) = {
    for (line <- source.getLines) {
      val pc :: addr :: data :: Nil = line.split(' ').toList
      do {
        step(1)
      } while (peek(c.io.regWen) == 0 || peek(c.io.regWaddr) == 0)
      expect(c.io.pc, BigInt(pc, 16))
      expect(c.io.regWaddr, BigInt(addr, 16))
      expect(c.io.regWdata, BigInt(data, 16))
    }
  }

  def printTrace() = {
    step(1)
    println(f"pc:   0x${peek(c.io.pc)}%x")
    println(f"wen:  ${peek(c.io.regWen)}%d")
    println(f"addr: ${peek(c.io.regWaddr)}%d")
    println(f"data: 0x${peek(c.io.regWdata)}%x")
    println("")
  }

  if (traceFile.isEmpty) {
    for (i <- 0 until 10) {
      printTrace()
    }
  }
  else {
    runTrace(Source.fromFile(traceFile))
  }
}

object CoreTest extends App {
  var initFile = ""
  var traceFile = ""

  val manager = ArgParser(args, (o, v) => {
    o match {
      case Some("--init") | Some("-if") => initFile = v; true
      case Some("--trace") | Some("-tf") => traceFile = v; true
      case _ => false
    }
  })

  Driver.execute(() => new CoreWrapper(initFile), manager) {
    (c) => new CoreUnitTester(c, traceFile)
  }
}
