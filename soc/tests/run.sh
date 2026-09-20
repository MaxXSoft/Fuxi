#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
mkdir -p build/peripheral-tests
verilator --binary --timing --assert -Wno-fatal --top-module clint_axi_tb \
  --Mdir build/peripheral-tests/clint_axi \
  soc/tests/clint_axi_tb.sv soc/peripherals/clint/AXIBridge.v soc/peripherals/clint/Clint.v
build/peripheral-tests/clint_axi/Vclint_axi_tb
