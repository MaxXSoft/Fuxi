package sim

import cli.CliExit
import configs.ConfigDump
import scala.util.control.NonFatal
import utils.{CoreWrapper, TestDriver}

// Command-line simulation and trace generation are separate from fixed regressions.
object CoreRunner extends App {
  CoreRunnerOptions.parse(args.toSeq) match {
    case Left(exit) => exit.report()
    case Right(options) => options.config.resolve() match {
      case Left(error) => CliExit(1, s"CoreRunner: $error").report()
      case Right(parameters) =>
        if (options.dumpConfig) {
          println(ConfigDump(parameters))
        } else {
          try {
            TestDriver.run(new CoreWrapper(options.initFile.get.toString)(parameters)) { c =>
              val tester = new CoreTraceTester(c)
              options.traceFile match {
                case None => tester.printTrace()
                case Some(file) if options.genTrace => tester.generateTrace(file)
                case Some(file) => tester.compareTrace(file)
              }
            }
          } catch {
            case NonFatal(error) =>
              error.printStackTrace()
              sys.exit(1)
          }
        }
    }
  }
}
