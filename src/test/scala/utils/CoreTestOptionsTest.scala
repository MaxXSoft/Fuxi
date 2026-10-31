package utils

import java.nio.file.Files
import configs.CoreParamsKey

object CoreTestOptionsTest extends App {
  val directory = Files.createTempDirectory("fuxi cli tests ")
  val input = Files.createFile(directory.resolve("input image.txt")).toFile
  val trace = Files.createFile(directory.resolve("expected trace.txt")).toFile
  val output = directory.resolve("output trace.txt").toFile
  def parse(args: String*) = CoreTestOptions.parse(args)
  def accepted(args: String*) = parse(args: _*).fold(e => throw new AssertionError(e.message), identity)
  def rejected(args: String*): Unit = assert(parse(args: _*).left.exists(_.status != 0), args.mkString(" "))
  try {
    val short = accepted("-if", input.toString, "-tf", trace.toString, "-gt", "0")
    val long = accepted(s"--init-file=$input", s"--trace-file=$trace", "--gen-trace=false")
    assert(short == long && !long.genTrace)
    assert(accepted("-if", input.toString).traceFile.isEmpty)
    for (enabled <- Seq("1", "true")) {
      val options = accepted("--gen-trace", enabled, "--trace-file", output.toString,
        "--init-file", input.toString, "--config", "SmallConfig", "--hpm-counters", "4")
      assert(options.genTrace)
      assert(options.config.resolve().toOption.get(CoreParamsKey).hpmCounters == 4)
      assert(!output.exists(), "Parsing must not create or truncate trace output")
    }
    assert(parse("--help").left.exists(_.status == 0))
    assert(accepted("--dump-config").dumpConfig)
    rejected(); rejected("--init-file"); rejected("--init-file", "--trace-file")
    rejected("--init-file", directory.toString)
    rejected("-if", input.toString, "-tf", output.toString)
    rejected("-if", input.toString, "-gt", "true")
    rejected("-if", input.toString, "-gt", "maybe")
    rejected("-if", input.toString, "--gen-trace", "--trace-file", trace.toString)
    rejected("-if", input.toString, "--gen-trace", "true", "--trace-file", "--help")
    rejected("-if", input.toString, "--gen-trace", "true", "--trace-file", "")
    rejected("-if", input.toString, "-tf", directory.toString, "-gt", "1")
    rejected("-if", input.toString, "-tf", directory.resolve("missing/out").toString, "-gt", "true")
    rejected("-if", input.toString, "--init-file", input.toString)
    rejected("-if", input.toString, "--unknown")
    rejected("-if", input.toString, "--", "extra")
    println("Core test CLI aliases, booleans, paths and failures passed")
  } finally {
    Files.delete(input.toPath)
    Files.delete(trace.toPath)
    Files.delete(directory)
  }
}
