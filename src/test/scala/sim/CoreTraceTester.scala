package sim

import java.io.{File, PrintWriter}
import scala.io.Source
import utils.{CoreChecks, CoreTester, CoreWrapper, ExpectedWriteback}

class CoreTraceTester(c: CoreWrapper) extends CoreTester(c) {
  private val endFlag = BigInt("deadc0de", 16)

  // perform trace comparison
  private def runTrace(source: Source): Unit = {
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

  def compareTrace(file: File): Unit = {
    val source = Source.fromFile(file)
    try runTrace(source) finally source.close()
  }
}
