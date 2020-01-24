package utils

import chisel3.iotesters.TesterOptionsManager

object ArgParser {
  type OptionCallback = (Option[String], String) => Boolean

  private def isOpt(s: String) = s(0) == '-'

  private def matchArgs(args: List[String],
                        opts: OptionCallback): Boolean = {
    args match {
      case Nil => true
      case opt :: value :: tail if isOpt(opt) => {
        if (!opts(Some(opt), value)) false else matchArgs(tail, opts)
      }
      case value :: opt :: _ if isOpt(opt) => {
        if (!opts(None, value)) false else matchArgs(args.tail, opts)
      }
      case value :: Nil => {
        opts(None, value)
      }
    }
  }

  def apply(args: Array[String], opts: OptionCallback) = {
    val manager = new TesterOptionsManager()
    manager.doNotExitOnHelp()
    manager.parse(args)
    if (!matchArgs(args.toList, opts)) {
      println("Error(ArgParser): Invalid argument list.")
      sys.exit(1)
    }
    manager
  }
}
