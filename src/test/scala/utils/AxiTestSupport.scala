package utils

import axi.AxiMaster

case class AxiReadRequest(address: BigInt, beats: Int, id: BigInt)

// Signal helpers deliberately leave clock ownership with each test.
trait AxiTestSupport { this: PeekPokeTester[_] =>
  protected def idleAxi(port: AxiMaster): Unit = {
    poke(port.readAddr.ready, false)
    driveAxiRead(port, 0, last = false, valid = false)
    poke(port.writeAddr.ready, false)
    poke(port.writeData.ready, false)
    poke(port.writeResp.valid, false)
    poke(port.writeResp.bits.id, 0)
    poke(port.writeResp.bits.resp, 0)
  }

  protected def driveAxiRead(port: AxiMaster, data: BigInt, last: Boolean,
                             response: Int = 0, id: BigInt = 0, valid: Boolean = true): Unit = {
    poke(port.readData.valid, valid)
    poke(port.readData.bits.data, data)
    poke(port.readData.bits.last, last)
    poke(port.readData.bits.resp, response)
    poke(port.readData.bits.id, id)
  }

  // One outstanding, word-sized INCR read burst. Address acceptance and beat
  // availability can be delayed independently. Once offered, a response stays
  // stable until accepted, even if allowData becomes false under backpressure.
  protected class AxiReadResponder(port: AxiMaster, readWord: BigInt => BigInt,
                                   response: (AxiReadRequest, Int) => Int = (_, _) => 0) {
    private var active: Option[AxiReadRequest] = None
    private var beat = 0
    private var offered: Option[(BigInt, Int)] = None
    private var acceptedAddress: Option[AxiReadRequest] = None
    private var acceptedData = false
    private var sampled = false
    private val history = scala.collection.mutable.ArrayBuffer.empty[AxiReadRequest]
    def requests: Seq[AxiReadRequest] = history.toVector

    // Drive and sample before the test's edge; return an accepted AR for any
    // scenario-specific assertions. Call afterStep exactly once after that edge.
    def beforeStep(allowAddress: Boolean = true, allowData: Boolean = true): Option[AxiReadRequest] = {
      require(!sampled, "Missing AXI afterStep")
      poke(port.readAddr.ready, active.isEmpty && allowAddress)
      if (offered.isEmpty && allowData) active.foreach { request =>
        offered = Some(readWord(request.address + beat * 4) -> response(request, beat))
      }
      driveAxiRead(port, offered.map(_._1).getOrElse(BigInt(0)),
        last = active.exists(request => beat == request.beats - 1),
        response = offered.map(_._2).getOrElse(0),
        id = active.map(_.id).getOrElse(BigInt(0)), valid = offered.nonEmpty)
      acceptedData = offered.nonEmpty && peek(port.readData.ready) != 0
      acceptedAddress = if (peek(port.readAddr.valid) != 0 && peek(port.readAddr.ready) != 0) {
        expect(port.readAddr.bits.size, 2)
        expect(port.readAddr.bits.burst, 1)
        Some(AxiReadRequest(peek(port.readAddr.bits.addr),
          peek(port.readAddr.bits.len).toInt + 1, peek(port.readAddr.bits.id)))
      } else None
      sampled = true
      acceptedAddress
    }

    def afterStep(): Unit = {
      require(sampled, "Missing AXI beforeStep")
      if (acceptedData) {
        offered = None
        beat += 1
        if (beat == active.get.beats) active = None
      }
      acceptedAddress.foreach { request =>
        active = Some(request)
        beat = 0
        history += request
      }
      sampled = false
    }
  }
}
