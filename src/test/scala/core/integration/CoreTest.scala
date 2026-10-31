package core.integration

import cli.CliExit
import configs.ConfigDump
import scala.io.Source
import java.io.{File, PrintWriter}

import utils.{CoreTestOptions, CoreChecks, CoreTester, CoreWrapper, ExpectedWriteback, TestDriver}

class CoreUnitTester(c: CoreWrapper, traceFile: String, genTrace: Boolean)
      extends CoreTester(c) {
  val endFlag = BigInt("deadc0de", 16)

  // perform trace comparison
  def runTrace(source: Source) = {
    for (line <- source.getLines()) {
      val pc :: addr :: data :: Nil = line.split(' ').toList
      runUntil(10000, s"waiting for trace PC $pc") {
        step(1)
        peek(c.io.regWen) != 0 && peek(c.io.regWaddr) != 0
      }
      CoreChecks.writeback(sampleDebug(c.io),
        ExpectedWriteback(BigInt(pc, 16), addr.toInt, BigInt(data, 16)))
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
  } else if (!genTrace) {
    val source = Source.fromFile(traceFile)
    try runTrace(source) finally source.close()
  } else {
    generateTrace(new File(traceFile))
  }
}

object CoreTest extends App {
  CoreTestOptions.parse(args.toSeq) match {
    case Left(exit) => exit.report()
    case Right(options) => options.config.resolve() match {
      case Left(error) => CliExit(1, s"CoreTest: $error").report()
      case Right(parameters) =>
        if (options.dumpConfig) {
          println(ConfigDump(parameters))
        } else if (!TestDriver.execute(Array.empty, () =>
            new CoreWrapper(options.initFile.get.toString)(parameters)) { c =>
              new CoreUnitTester(c, options.traceFile.fold("")(_.toString), options.genTrace)
            }) {
          sys.exit(1)
        }
    }
  }
}
