package configs

import java.lang.reflect.InvocationTargetException
import org.chipsalliance.cde.config.{Config, Parameters}
import scala.util.control.NonFatal

object ConfigLoader {
  def load(name: String): Either[String, Parameters] = {
    val qualified = if (name.contains('.')) name else s"configs.$name"
    try {
      val cls = Class.forName(qualified, false, Thread.currentThread().getContextClassLoader)
        .asSubclass(classOf[Config])
      Right(cls.getConstructor().newInstance())
    } catch {
      case _: ClassNotFoundException => Left(s"Config class '$qualified' was not found on the classpath")
      case _: ClassCastException => Left(s"'$qualified' must extend org.chipsalliance.cde.config.Config")
      case _: NoSuchMethodException => Left(s"'$qualified' must have a public no-argument constructor")
      case e: InvocationTargetException => Left(s"Config '$qualified' failed: ${e.getCause}")
      case NonFatal(e) => Left(s"Cannot load config '$qualified': $e")
      case e: LinkageError => Left(s"Cannot link config '$qualified': $e")
    }
  }
}
