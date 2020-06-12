`timescale 1ns / 1ps

module SoC_tb();

  reg   clk, rst_n;
  wire  cfg_si, cfg_so, cfg_ss;

  SoC soc_inst(
    .clk            (clk),
    .rst_n          (rst_n),

    .UART_rxd       (1'b0),
    .UART_txd       (),

    .SPI_FLASH_mosi (),
    .SPI_FLASH_miso (),
    .SPI_FLASH_ss   (),
    .SPI_FLASH_sck  (),
    .SPI_FLASH_io2  (),
    .SPI_FLASH_io3  (),

    .CFG_FLASH_mosi (cfg_si),
    .CFG_FLASH_miso (cfg_so),
    .CFG_FLASH_ss   (cfg_ss),

    .VGA_r          (),
    .VGA_g          (),
    .VGA_b          (),
    .VGA_hsync      (),
    .VGA_vsync      (),

    .led            (),
    .led_rg0        (),
    .led_rg1        (),
    .num_csn        (),
    .num_a_g        (),
    .num_a_g_dp     (),
    .btn_key_col    (),
    .btn_key_row    (4'b0),
    .btn_step       (2'b0),
    .switch         (8'b0),

    .DDR3_dq        (),
    .DDR3_addr      (),
    .DDR3_ba        (),
    .DDR3_ras_n     (),
    .DDR3_cas_n     (),
    .DDR3_we_n      (),
    .DDR3_odt       (),
    .DDR3_reset_n   (),
    .DDR3_cke       (),
    .DDR3_dm        (),
    .DDR3_dqs_p     (),
    .DDR3_dqs_n     (),
    .DDR3_ck_p      (),
    .DDR3_ck_n      (),

    .MDIO_mdc       (),
    .MDIO_mdio      (),
    .MII_col        (1'b0),
    .MII_crs        (1'b0),
    .MII_rst_n      (),
    .MII_rx_clk     (1'b0),
    .MII_rx_dv      (1'b0),
    .MII_rx_er      (1'b0),
    .MII_rxd        (4'b0),
    .MII_tx_clk     (1'b0),
    .MII_tx_en      (),
    .MII_tx_er      (),
    .MII_txd        (),

    .LCD_data       (),
    .LCD_nrst       (),
    .LCD_csel       (),
    .LCD_rd         (),
    .LCD_rs         (),
    .LCD_wr         (),
    .LCD_lighton    ()
  );

  s25fl128s cfg_flash(
    .SI             (cfg_si),
    .SO             (cfg_so),
    .SCK            (clk),
    .CSNeg          (cfg_ss),
    .RSTNeg         (1'b1),
    .HOLDNeg        (),
    .WPNeg          ()
  );

  always begin
    #5 clk <= ~clk;
  end

  initial begin
    clk <= 0;
    rst_n <= 0;
    #7 rst_n <= 1;
  end

endmodule
