package io

import chisel3._

// interface of stage's IO
abstract class StageIO extends Bundle {
  def default()
}
