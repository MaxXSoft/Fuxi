# Fuxi SoC

Fuxi SoC is a simple system-on-chip based on Fuxi processor. This design can be synthesised and implemented on development board with Xilinx 7 Series FPGA.

Fuxi SoC has already been tested on Loongson FPGA development board (with Xilinx XC7A200TFBG676-2).

## Requirement

* Xilinx Vivado 2018.3.
* Xilinx FPGA.

## Address Space Mapping of Peripherals

| Peripheral            | Start       | End         | Length  | Type      |
| -                     | -           | -           | -       | -         |
| On-chip ROM           | 0x00000000  | 0x0000FFFF  | 64KB    | Memory    |
| DDR3 Controller       | 0x80000000  | 0x87FFFFFF  | 128MB   | Memory    |
| CFG Flash Controller  | 0x10000000  | 0x10FFFFFF  | 16MB    | Memory    |
| CLINT                 | 0x11000000  | 0x11000FFF  | 4KB     | Register  |
| PLIC                  | 0x11010000  | 0x1101FFFF  | 64KB    | Register  |
| SPI Flash Controller  | 0x11020000  | 0x11020FFF  | 4KB     | Register  |
| VGA Controller        | 0x11030000  | 0x1103FFFF  | 64KB    | Register  |
| UART Controller       | 0x11040000  | 0x11041FFF  | 8KB     | Register  |
| LCD Controller        | 0x11050000  | 0x11050FFF  | 4KB     | Register  |
| Ethernet Controller   | 0x11060000  | 0x1106FFFF  | 64KB    | Register  |
| GPIO Controller       | 0x11070000  | 0x1107FFFF  | 64KB    | Register  |

# References

Fuxi SoC is heavily influenced by SoC part of [NonTrivialMIPS](https://github.com/trivialmips/nontrivial-mips) project.
