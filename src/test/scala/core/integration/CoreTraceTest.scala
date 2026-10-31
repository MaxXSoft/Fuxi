package core.integration

import java.io.File
import org.scalatest.funsuite.AnyFunSuite
import sim.CoreTraceTester
import utils.DefaultTestConfig._
import utils.{CoreWrapper, TestDriver}

class CoreTraceTest extends AnyFunSuite {
  private def resource(name: String): File = {
    val url = Option(getClass.getResource(s"/$name"))
      .getOrElse(fail(s"Missing test resource: $name"))
    new File(url.toURI)
  }

  for (program <- Seq("fib", "mdu", "mecall", "secall")) {
    test(s"$program matches its reference trace") {
      val image = resource(s"$program.txt")
      val trace = resource(s"${program}_trace.txt")
      TestDriver.run(new CoreWrapper(image.toString)) { c =>
        new CoreTraceTester(c).compareTrace(trace)
      }
    }
  }
}
