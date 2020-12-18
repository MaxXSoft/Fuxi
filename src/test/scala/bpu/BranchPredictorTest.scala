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
  var (lastBranch, lastTaken, lastIndex, lastPc, lastTarget) =
      (false, false, 0, 0, 0)
  
  var i = 0
  while (i < pcSeq.length) {
    val (pc, branch, taken, target) = pcSeq(i)
    // run next cycle
    poke(c.io.branchInfo.branch, lastBranch)
    poke(c.io.branchInfo.jump, false)
    poke(c.io.branchInfo.taken, lastTaken)
    poke(c.io.branchInfo.index, lastIndex)
    poke(c.io.branchInfo.pc, lastPc)
    poke(c.io.branchInfo.target, lastTarget)
    poke(c.io.lookupPc, pc)
    step(1)
    // check current status
    if (peek(c.io.predTaken) == (if (taken) 1 else 0) &&
        (!taken || (taken && peek(c.io.predTarget) == target))) {
      println(f"current: 0x$pc%x, target: 0x$target%x")
      i += 1
    }
    else {
      println(f"current: 0x$pc%x, MISS!, index: $lastIndex%d")
    }
    // update last status
    lastBranch = branch
    lastTaken = taken
    lastIndex = peek(c.io.predIndex).intValue
    lastPc = pc
    lastTarget = target
  }
}

object BranchPredictorTest extends App {
  if (!Driver.execute(args, () => new BranchPredictor) {
    (c) => new BpUnitTester(c)
  }) sys.exit(1)
}
