package bus

import chisel3._
import axi.AxiMaster
import utils.{AxiReadRequest, AxiTestSupport, PeekPokeTester, TestDriver}

class AxiReadTestHarness extends Module {
  val io = IO(new Bundle {
    val request = Input(Bool())
    val address = Input(UInt(32.W))
    val length = Input(UInt(8.W))
    val id = Input(UInt(4.W))
    val ready = Input(Bool())
    val axi = new AxiMaster(32, 32)
  })
  io.axi.init()
  io.axi.readAddr.valid := io.request
  io.axi.readAddr.bits.addr := io.address
  io.axi.readAddr.bits.len := io.length
  io.axi.readAddr.bits.id := io.id
  io.axi.readAddr.bits.size := 2.U
  io.axi.readAddr.bits.burst := 1.U
  io.axi.readData.ready := io.ready
}

class AxiReadSupportTester(c: AxiReadTestHarness) extends PeekPokeTester(c) with AxiTestSupport {
  val memory = scala.collection.mutable.Map(BigInt(0x1000) -> BigInt(0x11223344), BigInt(0x1004) -> BigInt(0x55667788))
  val responder = new AxiReadResponder(c.io.axi, memory.apply,
    (_, beat) => if (beat == 1) 2 else 0)
  idleAxi(c.io.axi)
  poke(c.io.request, true)
  poke(c.io.address, 0x1000)
  poke(c.io.length, 1)
  poke(c.io.id, 7)
  poke(c.io.ready, false)

  def cycle(address: Boolean = true, data: Boolean = true)(check: Option[AxiReadRequest] => Unit): Unit = {
    val request = responder.beforeStep(address, data)
    check(request)
    step(1)
    responder.afterStep()
  }
  def expectBeat(data: BigInt, id: Int, last: Boolean, response: Int): Unit = {
    expect(c.io.axi.readData.valid, true)
    expect(c.io.axi.readData.bits.data, data)
    expect(c.io.axi.readData.bits.id, id)
    expect(c.io.axi.readData.bits.last, last)
    expect(c.io.axi.readData.bits.resp, response)
  }

  for (_ <- 0 until 2) cycle(address = false) { request =>
    assert(request.isEmpty)
    expect(c.io.axi.readAddr.ready, false)
    expect(c.io.axi.readData.valid, false)
  }
  cycle() { request => assert(request.contains(AxiReadRequest(0x1000, 2, 7))) }
  // Subsequent live AR fields must not change ownership of the pending burst.
  poke(c.io.request, false)
  poke(c.io.address, 0x9000)
  poke(c.io.length, 7)
  poke(c.io.id, 9)
  cycle(data = false) { _ => expect(c.io.axi.readData.valid, false) }
  cycle() { _ => expectBeat(0x11223344, 7, last = false, response = 0) }
  memory(0x1000) = 0
  for (_ <- 0 until 3) cycle(data = false) { _ =>
    expectBeat(0x11223344, 7, last = false, response = 0)
    expect(c.io.axi.readAddr.ready, false)
  }
  poke(c.io.ready, true)
  cycle(data = false) { _ => expectBeat(0x11223344, 7, last = false, response = 0) }
  cycle(data = false) { _ => expect(c.io.axi.readData.valid, false) }
  cycle() { _ => expectBeat(0x55667788, 7, last = true, response = 2) }

  // The next single-beat request gets its own ID and LAST without old beat state.
  poke(c.io.request, true)
  poke(c.io.address, 0x1004)
  poke(c.io.length, 0)
  cycle() { request => assert(request.contains(AxiReadRequest(0x1004, 1, 9))) }
  poke(c.io.request, false)
  cycle() { _ => expectBeat(0x55667788, 9, last = true, response = 0) }
  cycle() { _ => expect(c.io.axi.readData.valid, false) }
  assert(responder.requests == Seq(AxiReadRequest(0x1000, 2, 7), AxiReadRequest(0x1004, 1, 9)))
}

object AxiTestSupportTest extends App {
  if (!TestDriver.execute(args, () => new AxiReadTestHarness)(c => new AxiReadSupportTester(c))) sys.exit(1)
}
