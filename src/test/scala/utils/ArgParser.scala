package utils

object ArgParser {
  type OptionCallback = (Option[String], String) => Boolean

  private def isOpt(s: String) = s.startsWith("-")

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
      case _ => false
    }
  }

  def apply(args: Array[String], opts: OptionCallback) = {
    if (!matchArgs(args.toList, opts)) {
      println("Error(ArgParser): Invalid argument list.")
      sys.exit(1)
    }
  }
}
