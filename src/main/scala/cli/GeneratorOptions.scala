package cli

import scopt.OParser

case class GeneratorOptions(
  config: ConfigOptions = ConfigOptions(),
  targetDir: Option[String] = None,
  dumpConfig: Boolean = false,
  stageArgs: Seq[String] = Seq.empty,
)

object GeneratorOptions {
  private val builder = OParser.builder[GeneratorOptions]
  import builder._

  private val parser = OParser.sequence(
    programName("Fuxi"),
    head("Fuxi Verilog generator"),
    ConfigOptions.parser[GeneratorOptions](_.config, (c, config) => c.copy(config = config)),
    opt[String]("target-dir").maxOccurs(1).valueName("<directory>")
      .validate(s => if (s.nonEmpty && !s.startsWith("-")) success
                     else failure("Invalid target directory; use ./ for names starting with '-'"))
      .action((s, c) => c.copy(targetDir = Some(s))),
    opt[Unit]("dump-config").maxOccurs(1)
      .text("Print effective parameters and exit without generating RTL")
      .action((_, c) => c.copy(dumpConfig = true)),
    help("help").abbr("h"),
    note("Pass additional Chisel/firtool arguments after --."),
  )

  def parse(args: Seq[String]): Either[CliExit, GeneratorOptions] = {
    val (own, remainder) = args.span(_ != "--")
    CommandLine.parse(parser, own, GeneratorOptions())
      .map(_.copy(stageArgs = remainder.drop(1)))
  }
}
