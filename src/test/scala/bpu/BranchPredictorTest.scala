package bpu

import chisel3.iotesters.{Driver, PeekPokeTester}

object InstType extends Enumeration {
  val Normal, Branch, Jump = Value
}

class BpUnitTester(c: BranchPredictor) extends PeekPokeTester(c) {
  val pcSeq = Array(
    //   pc        br   taken    target
    (0x00000200, false, false, 0x00000000),
    (0x00000204, false, false, 0x00000000),
    (0x00000208, false, false, 0x00000000),
    (0x0000020c, true,  true,  0x00000200),
    (0x00000200, false, false, 0x00000000),
    (0x00000204, false, false, 0x00000000),
    (0x00000208, false, false, 0x00000000),
    (0x0000020c, true,  true,  0x00000200),
    (0x00000200, false, false, 0x00000000),
    (0x00000204, false, false, 0x00000000),
    (0x00000208, false, false, 0x00000000),
    (0x0000020c, true,  true,  0x00000200),
    (0x00000200, false, false, 0x00000000),
    (0x00000204, false, false, 0x00000000),
    (0x00000208, false, false, 0x00000000),
    (0x0000020c, true,  false, 0x00000000),
    (0x00000210, false, false, 0x00000000),
    (0x00000214, false, false, 0x00000000),
    (0x00000218, false, false, 0x00000000),
    (0x0000021c, false, false, 0x00000000),
  )
  var lastIndex = 0
  
  var i = 0
  while (i < pcSeq.length) {
    val (pc, branch, taken, target) = pcSeq(i)
    poke(c.io.branch, branch)
    poke(c.io.jump, false)
    poke(c.io.taken, taken)
    poke(c.io.index, lastIndex)
    poke(c.io.pc, pc)
    poke(c.io.target, target)
    poke(c.io.lookupPc, pc)
    step(1)
    if (peek(c.io.predTaken) == (if (taken) 1 else 0) &&
        (!taken || (taken && peek(c.io.predTarget) == target))) {
      println(f"current: 0x$pc%x, target: 0x$target%x")
      i += 1
    }
    else {
      println(f"current: 0x$pc%x, MISS!")
    }
    lastIndex = peek(c.io.predIndex).intValue
  }
}

object BranchPredictorTest extends App {
  Driver.execute(args, () => new BranchPredictor) {
    (c) => new BpUnitTester(c)
  }
}
