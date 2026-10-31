package cli

import scopt.{OEffect, OParser}

// Parsing never exits the JVM, prints, loads a class, or elaborates hardware.
case class CliExit(status: Int, message: String) {
  def report(): Unit = {
    if (status == 0) println(message)
    else { Console.err.println(message); sys.exit(status) }
  }
}

object CommandLine {
  def parse[C](parser: OParser[_, C], args: Seq[String], initial: C): Either[CliExit, C] = {
    val (result, effects) = OParser.runParser(parser, args, initial)
    val errors = effects.collect {
      case OEffect.ReportError(message) => message
      case OEffect.DisplayToErr(message) => message
    }
    if (result.isEmpty || errors.nonEmpty) Left(CliExit(1, errors.mkString("\n")))
    else if (effects.exists { case OEffect.Terminate(Right(_)) => true; case _ => false }) {
      Left(CliExit(0, effects.collect { case OEffect.DisplayToOut(message) => message }.mkString("\n")))
    } else Right(result.get)
  }
}
