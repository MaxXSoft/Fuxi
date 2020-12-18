package core

import chisel3._
import chisel3.iotesters.{Driver, PeekPokeTester}
import scala.io.Source
import java.io.{File, PrintWriter}

import io._
import sim._
import utils.ArgParser

class CoreWrapper(initFile: String) extends Module {
  val io    = IO(new DebugIO)
  val core  = Module(new Core)
  val rom   = Module(new ROM(initFile))
  val ram   = Module(new RAM)

  core.io.irq.timer   := false.B
  core.io.irq.soft    := false.B
  core.io.irq.extern  := false.B
  core.io.rom         <> rom.io
  core.io.ram         <> ram.io
  core.io.debug       <> io
}

class CoreUnitTester(c: CoreWrapper, traceFile: String, genTrace: Boolean)
      extends PeekPokeTester(c) {
  val endFlag = BigInt("deadc0de", 16)

  // perform trace comparison
  def runTrace(source: Source) = {
    for (line <- source.getLines) {
      val pc :: addr :: data :: Nil = line.split(' ').toList
      do {
        step(1)
      } while (peek(c.io.regWen) == 0 || peek(c.io.regWaddr) == 0)
      expect(c.io.pc, BigInt(pc, 16))
      expect(c.io.regWaddr, BigInt(addr, 10))
      expect(c.io.regWdata, BigInt(data, 16))
    }
  }

  // generate trace using Fuxi core
  def generateTrace(file: File) = {
    val p = new PrintWriter(file)
    try {
      do {
        step(1)
        println(s"cycle: $t")
        if (peek(c.io.regWen) != 0 && peek(c.io.regWaddr) != 0) {
          p.print(f"${peek(c.io.pc)}%08x ")
          p.print(f"${peek(c.io.regWaddr)}%02d ")
          p.print(f"${peek(c.io.regWdata)}%08x\n")
        }
      } while (peek(c.io.regWen) == 0 || peek(c.io.regWdata) != endFlag)
    }
    finally {
      p.close()
    }
  }

  // print trace to console
  def printTrace() = {
    do {
      step(1)
      println(s"cycle: $t")
      println(f"    pc:   0x${peek(c.io.pc)}%x")
      println(f"    wen:  ${peek(c.io.regWen)}%d")
      println(f"    addr: ${peek(c.io.regWaddr)}%d")
      println(f"    data: 0x${peek(c.io.regWdata)}%x")
    } while (peek(c.io.regWen) == 0 || peek(c.io.regWdata) != endFlag)
  }

  if (traceFile.isEmpty) {
    printTrace()
  }
  else if (!genTrace) {
    runTrace(Source.fromFile(traceFile))
  }
  else {
    generateTrace(new File(traceFile))
  }
}

object CoreTest extends App {
  var initFile = ""
  var traceFile = ""
  var genTrace = false

  val manager = ArgParser(args, (o, v) => {
    o match {
      case Some("--init-file") | Some("-if") => initFile = v; true
      case Some("--trace-file") | Some("-tf") => traceFile = v; true
      case Some("--gen-trace") | Some("-gt") => genTrace = v != "0"; true
      case _ => false
    }
  })

  if (!Driver.execute(() => new CoreWrapper(initFile), manager) {
    (c) => new CoreUnitTester(c, traceFile, genTrace)
  }) sys.exit(1)
}
