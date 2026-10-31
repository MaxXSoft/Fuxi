package utils

import configs.DefaultConfig
import org.chipsalliance.cde.config.Parameters

// Tests explicitly import this baseline. Production generators must supply p.
object DefaultTestConfig {
  implicit val p: Parameters = new DefaultConfig
}
