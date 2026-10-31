package utils

import java.io.File
import cli.{CliExit, CommandLine, ConfigOptions}
import scopt.OParser

case class CoreTestOptions(
  config: ConfigOptions = ConfigOptions(),
  initFile: Option[File] = None,
  traceFile: Option[File] = None,
  genTrace: Boolean = false,
  dumpConfig: Boolean = false,
)

object CoreTestOptions {
  private val builder = OParser.builder[CoreTestOptions]
  import builder._

  private def validPath(file: File): Boolean =
    file.getPath.nonEmpty && !file.getPath.startsWith("-")
  private def readable(file: File): Boolean = file.isFile && file.canRead
  private def writable(file: File): Boolean =
    if (file.exists()) file.isFile && file.canWrite
    else Option(file.getAbsoluteFile.getParentFile).exists(p => p.isDirectory && p.canWrite)

  private val parser = OParser.sequence(
    programName("core.integration.CoreTest"),
    ConfigOptions.parser[CoreTestOptions](_.config, (c, config) => c.copy(config = config)),
    opt[File]("init-file").abbr("if").maxOccurs(1).valueName("<file>")
      .text("ROM image (required unless --dump-config)")
      .validate { f =>
        if (validPath(f) && readable(f)) success
        else failure(s"ROM image is not a readable file: $f")
      }
      .action { (f, c) => c.copy(initFile = Some(f.getAbsoluteFile)) },
    opt[File]("trace-file").abbr("tf").maxOccurs(1).valueName("<file>")
      .text("Expected trace, or output trace when --gen-trace true")
      .validate { f =>
        if (validPath(f)) success
        else failure("Invalid trace path; use ./ for names starting with '-'")
      }
      .action { (f, c) => c.copy(traceFile = Some(f.getAbsoluteFile)) },
    opt[String]("gen-trace").abbr("gt").maxOccurs(1).valueName("<true|false|1|0>")
      .text("Generate a trace (default: false); requires --trace-file")
      .validate { s =>
        if (Set("true", "false", "1", "0")(s)) success
        else failure("--gen-trace expects true, false, 1 or 0")
      }
      .action { (s, c) => c.copy(genTrace = s == "true" || s == "1") },
    opt[Unit]("dump-config").maxOccurs(1).text("Print effective parameters without simulation")
      .action { (_, c) => c.copy(dumpConfig = true) },
    help("help").abbr("h"),
    checkConfig { c =>
      if (!c.dumpConfig && c.initFile.isEmpty) {
        failure("--init-file is required")
      } else if (c.genTrace && c.traceFile.isEmpty) {
        failure("--gen-trace requires --trace-file")
      } else c.traceFile match {
        case Some(f) if c.genTrace && !writable(f) => failure(s"Trace output is not writable: $f")
        case Some(f) if !c.genTrace && !readable(f) => failure(s"Trace is not a readable file: $f")
        case _ => success
      }
    }
  )

  def parse(args: Seq[String]): Either[CliExit, CoreTestOptions] =
    CommandLine.parse(parser, args, CoreTestOptions())
}
