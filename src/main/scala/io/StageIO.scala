package io

import chisel3._

// interface of stage's IO
class StageIO[T <: StageIO[T]] extends Bundle {
  this: T =>
  def default() = 0.U.asTypeOf(this)
}
