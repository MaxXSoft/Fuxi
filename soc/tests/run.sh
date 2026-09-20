#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
mkdir -p build/peripheral-tests
if [ "$#" -eq 0 ]; then set -- clint gpio; fi
for peripheral in "$@"; do
  case "$peripheral" in
    clint) sources="soc/peripherals/clint/AXIBridge.v soc/peripherals/clint/Clint.v" ;;
    gpio) sources="soc/peripherals/gpio/confreg.v" ;;
    *) echo "Unknown peripheral test: $peripheral" >&2; exit 2 ;;
  esac
  # The source list contains fixed repository paths without spaces.
  verilator --binary --timing --assert -Wno-fatal --top-module "${peripheral}_axi_tb" \
    --Mdir "build/peripheral-tests/${peripheral}_axi" \
    "soc/tests/${peripheral}_axi_tb.sv" $sources
  "build/peripheral-tests/${peripheral}_axi/V${peripheral}_axi_tb"
done
