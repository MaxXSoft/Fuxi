package utils

import chisel3._
import chisel3.experimental.{SourceInfo, UnlocatableSourceInfo}
import chisel3.simulator.{ChiselSim, PeekPokeAPI}

import scala.util.Random
import scala.util.control.NonFatal

abstract class PeekPokeTester[T <: Module](val dut: T) extends PeekPokeAPI {
  protected val rnd = new Random
  protected var t = 0L

  private implicit val sourceInfo: SourceInfo = UnlocatableSourceInfo

  protected def step(cycles: Int): Unit = {
    toTestableClock(dut.clock).step(cycles)
    t += cycles
  }

  protected def poke(signal: Element, value: BigInt): Unit = signal match {
    case bool: Bool => toTestableBool(bool).poke(value != 0)
    case uint: UInt => toTestableUInt(uint).poke(normalizeUInt(uint, value))
    case sint: SInt => toTestableSInt(sint).poke(value)
    case other => throw new IllegalArgumentException(s"Unsupported poke target: ${other.getClass.getName}")
  }

  protected def poke(signal: Bool, value: Boolean): Unit =
    toTestableBool(signal).poke(value)

  protected def poke(signal: Element, value: Element): Unit =
    poke(signal, value.litValue)

  protected def peek(signal: Element): BigInt =
    toTestableData(signal).peek().litValue

  protected def expect(signal: Element, expected: BigInt): Unit = signal match {
    case bool: Bool => toTestableBool(bool).expect(expected != 0)
    case uint: UInt => toTestableUInt(uint).expect(normalizeUInt(uint, expected))
    case sint: SInt => toTestableSInt(sint).expect(expected)
    case other => throw new IllegalArgumentException(s"Unsupported expect target: ${other.getClass.getName}")
  }

  protected def expect(signal: Bool, expected: Boolean): Unit =
    toTestableBool(signal).expect(expected)

  protected def expect(signal: Element, expected: Element): Unit =
    expect(signal, expected.litValue)

  private def normalizeUInt(signal: UInt, value: BigInt): BigInt = {
    val width = signal.getWidth
    if (value < 0 && width > 0) value & ((BigInt(1) << width) - 1) else value
  }
}

object TestDriver extends ChiselSim {
  def execute[T <: Module](
    _args: Array[String],
    generator: () => T
  )(tester: T => PeekPokeTester[T]): Boolean = {
    try {
      simulate(generator()) { dut =>
        tester(dut)
        ()
      }
      true
    } catch {
      case NonFatal(error) =>
        error.printStackTrace()
        false
    }
  }
}
