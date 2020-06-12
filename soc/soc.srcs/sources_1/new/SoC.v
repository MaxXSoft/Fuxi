// from https://github.com/trivialmips/nontrivial-mips
// modified by: MaxXing, 2020-05

`timescale 1ns / 1ps

`include "iobuf.v"

module SoC(
  input           clk,
  input           rst_n,
  // UART
  input           UART_rxd,
  output          UART_txd,
  // plugable SPI flash
  inout           SPI_FLASH_mosi,
  inout           SPI_FLASH_miso,
  inout           SPI_FLASH_ss,
  inout           SPI_FLASH_sck,
  inout           SPI_FLASH_io2,
  inout           SPI_FLASH_io3,
  // non-plugable CFG SPI flash
  inout           CFG_FLASH_mosi,
  inout           CFG_FLASH_miso,
  inout           CFG_FLASH_ss,
  // VGA (use inout for high-Z output)
  inout   [3:0]   VGA_r,
  inout   [3:0]   VGA_g,
  inout   [3:0]   VGA_b,
  output          VGA_hsync,
  output          VGA_vsync,
  // GPIO
  output  [15:0]  led,
  output  [1:0]   led_rg0,
  output  [1:0]   led_rg1,
  output  [7:0]   num_csn,
  output  [6:0]   num_a_g,
  output          num_a_g_dp,
  output  [3:0]   btn_key_col,
  input   [3:0]   btn_key_row,
  input   [1:0]   btn_step,
  input   [7:0]   switch,
  // DDR3
  inout   [15:0]  DDR3_dq,
  output  [12:0]  DDR3_addr,
  output  [2:0]   DDR3_ba,
  output          DDR3_ras_n,
  output          DDR3_cas_n,
  output          DDR3_we_n,
  output          DDR3_odt,
  output          DDR3_reset_n,
  output          DDR3_cke,
  output  [1:0]   DDR3_dm,
  inout   [1:0]   DDR3_dqs_p,
  inout   [1:0]   DDR3_dqs_n,
  output          DDR3_ck_p,
  output          DDR3_ck_n,
  // ethernet
  output          MDIO_mdc,
  inout           MDIO_mdio,
  input           MII_col,
  input           MII_crs,
  output          MII_rst_n,
  input           MII_rx_clk,
  input           MII_rx_dv,
  input           MII_rx_er,
  input   [3:0]   MII_rxd,
  input           MII_tx_clk,
  output          MII_tx_en,
  output          MII_tx_er,
  output  [3:0]   MII_txd,
  // LCD
  inout   [15:0]  LCD_data,
  output          LCD_nrst,
  output          LCD_csel,
  output          LCD_rd,
  output          LCD_rs,
  output          LCD_wr,
  output          LCD_lighton
);

  // plugable SPI flash
  `IOBUF_GEN(SPI_FLASH_mosi, SPI_FLASH_io0)
  `IOBUF_GEN(SPI_FLASH_miso, SPI_FLASH_io1)
  `IOBUF_GEN_SIMPLE(SPI_FLASH_io2)
  `IOBUF_GEN_SIMPLE(SPI_FLASH_io3)
  `IOBUF_GEN_SIMPLE(SPI_FLASH_ss)
  `IOBUF_GEN_SIMPLE(SPI_FLASH_sck)

  // non-plugable CFG SPI flash
  `IOBUF_GEN(CFG_FLASH_mosi, CFG_FLASH_io0)
  `IOBUF_GEN(CFG_FLASH_miso, CFG_FLASH_io1)
  `IOBUF_GEN_SIMPLE(CFG_FLASH_ss)

  // Ethernet
  `IOBUF_GEN_SIMPLE(MDIO_mdio)
  // not provided in Ethernet Lite
  assign MII_tx_er = 1'b0;

  // GPIO
  // not provided in confreg IP
  assign num_a_g_dp = 1'b0;

  // LCD
  `IOBUF_GEN_VEC(16, LCD_data, LCD_data_tri)

  // VGA
  wire [5:0] VGA_red, VGA_green, VGA_blue;
  genvar VGA_i;
  generate
    for (VGA_i = 0; VGA_i < 4; VGA_i = VGA_i+1) begin : VGA_gen
      // match on-board DAC built by resistor
      assign VGA_r[VGA_i] = VGA_red[VGA_i+2] ? 1'b1 : 1'bZ;
      assign VGA_g[VGA_i] = VGA_green[VGA_i+2] ? 1'b1 : 1'bZ;
      assign VGA_b[VGA_i] = VGA_blue[VGA_i+2] ? 1'b1 : 1'bZ;
    end
  endgenerate

  // initialize block design
  soc soc_inst(
    .clk              (clk),
    .rst_n            (rst_n),
    // UART
    .UART_txd         (UART_txd),
    .UART_rxd         (UART_rxd),
    .UART_ctsn        (1'b0),
    .UART_dcdn        (1'b0),
    .UART_dsrn        (1'b0),
    .UART_ri          (1'b1),
    // plugable SPI flash
    .SPI_FLASH_io0_i  (SPI_FLASH_io0_i),
    .SPI_FLASH_io0_o  (SPI_FLASH_io0_o),
    .SPI_FLASH_io0_t  (SPI_FLASH_io0_t),
    .SPI_FLASH_io1_i  (SPI_FLASH_io1_i),
    .SPI_FLASH_io1_o  (SPI_FLASH_io1_o),
    .SPI_FLASH_io1_t  (SPI_FLASH_io1_t),
    .SPI_FLASH_io2_i  (SPI_FLASH_io2_i),
    .SPI_FLASH_io2_o  (SPI_FLASH_io2_o),
    .SPI_FLASH_io2_t  (SPI_FLASH_io2_t),
    .SPI_FLASH_io3_i  (SPI_FLASH_io3_i),
    .SPI_FLASH_io3_o  (SPI_FLASH_io3_o),
    .SPI_FLASH_io3_t  (SPI_FLASH_io3_t),
    .SPI_FLASH_sck_i  (SPI_FLASH_sck_i),
    .SPI_FLASH_sck_o  (SPI_FLASH_sck_o),
    .SPI_FLASH_sck_t  (SPI_FLASH_sck_t),
    .SPI_FLASH_ss_i   (SPI_FLASH_ss_i),
    .SPI_FLASH_ss_o   (SPI_FLASH_ss_o),
    .SPI_FLASH_ss_t   (SPI_FLASH_ss_t),
    // non-plugable CFG SPI flash
    .CFG_FLASH_io0_i  (CFG_FLASH_io0_i),
    .CFG_FLASH_io0_o  (CFG_FLASH_io0_o),
    .CFG_FLASH_io0_t  (CFG_FLASH_io0_t),
    .CFG_FLASH_io1_i  (CFG_FLASH_io1_i),
    .CFG_FLASH_io1_o  (CFG_FLASH_io1_o),
    .CFG_FLASH_io1_t  (CFG_FLASH_io1_t),
    .CFG_FLASH_ss_i   (CFG_FLASH_ss_i),
    .CFG_FLASH_ss_o   (CFG_FLASH_ss_o),
    .CFG_FLASH_ss_t   (CFG_FLASH_ss_t),
    // VGA
    .VGA_hsync        (VGA_hsync),
    .VGA_vsync        (VGA_vsync),
    .VGA_red          (VGA_red),
    .VGA_green        (VGA_green),
    .VGA_blue         (VGA_blue),
    .VGA_clk          (),
    .VGA_de           (),
    .VGA_dps          (),
    // GPIO
    .led              (led),
    .led_rg0          (led_rg0),
    .led_rg1          (led_rg1),
    .num_csn          (num_csn),
    .num_a_g          (num_a_g),
    .btn_key_col      (btn_key_col),
    .btn_key_row      (btn_key_row),
    .btn_step         (btn_step),
    .switch           (switch),
    // DDR3
    .DDR3_dq          (DDR3_dq),
    .DDR3_addr        (DDR3_addr),
    .DDR3_ba          (DDR3_ba),
    .DDR3_ras_n       (DDR3_ras_n),
    .DDR3_cas_n       (DDR3_cas_n),
    .DDR3_we_n        (DDR3_we_n),
    .DDR3_odt         (DDR3_odt),
    .DDR3_reset_n     (DDR3_reset_n),
    .DDR3_cke         (DDR3_cke),
    .DDR3_dm          (DDR3_dm),
    .DDR3_dqs_p       (DDR3_dqs_p),
    .DDR3_dqs_n       (DDR3_dqs_n),
    .DDR3_ck_p        (DDR3_ck_p),
    .DDR3_ck_n        (DDR3_ck_n),
    // ethernet
    // .MII_tx_er is not connected
    .MDIO_mdc         (MDIO_mdc),
    .MDIO_mdio_i      (MDIO_mdio_i),
    .MDIO_mdio_o      (MDIO_mdio_o),
    .MDIO_mdio_t      (MDIO_mdio_t),
    .MII_col          (MII_col),
    .MII_crs          (MII_crs),
    .MII_rst_n        (MII_rst_n),
    .MII_rx_clk       (MII_rx_clk),
    .MII_rx_dv        (MII_rx_dv),
    .MII_rx_er        (MII_rx_er),
    .MII_rxd          (MII_rxd),
    .MII_tx_clk       (MII_tx_clk),
    .MII_tx_en        (MII_tx_en),
    .MII_txd          (MII_txd),
    // LCD
    .LCD_data_tri_i   (LCD_data_tri_i),
    .LCD_data_tri_o   (LCD_data_tri_o),
    .LCD_data_tri_t   (LCD_data_tri_t),
    .LCD_nrst         (LCD_nrst),
    .LCD_csel         (LCD_csel),
    .LCD_rd           (LCD_rd),
    .LCD_rs           (LCD_rs),
    .LCD_wr           (LCD_wr),
    .LCD_lighton      (LCD_lighton)
  );

endmodule
