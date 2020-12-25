`timescale 1ns / 1ps

/*
 * CLINT for Fuxi core
 *
 *  address:
 *    0x000: msip
 *    0x100: mtime lo
 *    0x104: mtime hi
 *    0x108: mtimecmp lo
 *    0x10c: mtimecmp hi
 */
module Clint(
  input           clk,
  input           clk_rtc,
  input           rst_n,
  // AXI slave interface
  input   [5:0]   axi_arid,
  input   [31:0]  axi_araddr,
  input   [7:0]   axi_arlen,
  input   [2:0]   axi_arsize,
  input   [1:0]   axi_arburst,
  input   [1:0]   axi_arlock,
  input   [3:0]   axi_arcache,
  input   [2:0]   axi_arprot,
  input           axi_arvalid,
  output          axi_arready,
  output  [5:0]   axi_rid,
  output  [31:0]  axi_rdata,
  output  [1:0]   axi_rresp,
  output          axi_rlast,
  output          axi_rvalid,
  input           axi_rready,
  input   [5:0]   axi_awid,
  input   [31:0]  axi_awaddr,
  input   [7:0]   axi_awlen,
  input   [2:0]   axi_awsize,
  input   [1:0]   axi_awburst,
  input   [1:0]   axi_awlock,
  input   [3:0]   axi_awcache,
  input   [2:0]   axi_awprot,
  input           axi_awvalid,
  output          axi_awready,
  input   [5:0]   axi_wid,
  input   [31:0]  axi_wdata,
  input   [3:0]   axi_wstrb,
  input           axi_wlast,
  input           axi_wvalid,
  output          axi_wready,
  output  [5:0]   axi_bid,
  output  [1:0]   axi_bresp,
  output          axi_bvalid,
  input           axi_bready,
  // interrupt signals
  output          intr_timer,
  output          intr_soft
);

  // address
  localparam      ADDR_MSIP     = 12'h000;
  localparam      ADDR_MTIME    = 12'h100,  ADDR_MTIMEH     = 12'h104;
  localparam      ADDR_MTIMECMP = 12'h108,  ADDR_MTIMECMPH  = 12'h10c;


  // convert AXI to peripheral interface
  wire            gpi_read, gpi_write;
  wire  [31:0]    gpi_addr, gpi_wdata;
  wire  [11:0]    gpi_peri_addr;
  reg   [31:0]    gpi_rdata;

  assign gpi_peri_addr = gpi_addr[11:0];

  AXIBridge axi_bridge(
    .clk          (clk),
    .rst_n        (rst_n),

    .axi_arid     (axi_arid),
    .axi_araddr   (axi_araddr),
    .axi_arlen    (axi_arlen),
    .axi_arsize   (axi_arsize),
    .axi_arburst  (axi_arburst),
    .axi_arlock   (axi_arlock),
    .axi_arcache  (axi_arcache),
    .axi_arprot   (axi_arprot),
    .axi_arvalid  (axi_arvalid),
    .axi_arready  (axi_arready),
    .axi_rid      (axi_rid),
    .axi_rdata    (axi_rdata),
    .axi_rresp    (axi_rresp),
    .axi_rlast    (axi_rlast),
    .axi_rvalid   (axi_rvalid),
    .axi_rready   (axi_rready),
    .axi_awid     (axi_awid),
    .axi_awaddr   (axi_awaddr),
    .axi_awlen    (axi_awlen),
    .axi_awsize   (axi_awsize),
    .axi_awburst  (axi_awburst),
    .axi_awlock   (axi_awlock),
    .axi_awcache  (axi_awcache),
    .axi_awprot   (axi_awprot),
    .axi_awvalid  (axi_awvalid),
    .axi_awready  (axi_awready),
    .axi_wid      (axi_wid),
    .axi_wdata    (axi_wdata),
    .axi_wstrb    (axi_wstrb),
    .axi_wlast    (axi_wlast),
    .axi_wvalid   (axi_wvalid),
    .axi_wready   (axi_wready),
    .axi_bid      (axi_bid),
    .axi_bresp    (axi_bresp),
    .axi_bvalid   (axi_bvalid),
    .axi_bready   (axi_bready),

    .gpi_read     (gpi_read),
    .gpi_write    (gpi_write),
    .gpi_addr     (gpi_addr),
    .gpi_wdata    (gpi_wdata),
    .gpi_rdata    (gpi_rdata)
  );


  // registers
  reg           msip;
  reg   [31:0]  mtime, mtime_r1, mtime_r2;
  reg   [31:0]  mtimeh, mtimeh_r1, mtimeh_r2;
  reg   [31:0]  mtimecmp, mtimecmph;
  wire  [63:0]  mtime_full, next_mtime_full;
  wire  [31:0]  next_mtime, next_mtimeh;

  assign mtime_full       = {mtimeh, mtime};
  assign next_mtime_full  = mtime_full + 64'b1;
  assign next_mtime       = next_mtime_full[31:0];
  assign next_mtimeh      = next_mtime_full[63:32];


  // interrupt signal output
  wire  [63:0]  mtime_r2_full, mtimecmp_full;
  reg           timer_intr;

  assign mtime_r2_full  = {mtimeh_r2, mtime_r2};
  assign mtimecmp_full  = {mtimecmph, mtimecmp};
  assign intr_soft      = msip;
  assign intr_timer     = timer_intr;

  always @(posedge clk) begin
    timer_intr <= mtime_r2_full >= mtimecmp_full;
  end


  // peripheral read
  always @(posedge clk) begin
    if (!rst_n) begin
      gpi_rdata <= 32'h0;
    end
    else if (gpi_read) begin
      case (gpi_peri_addr)
        ADDR_MSIP:      gpi_rdata <=  {31'b0, msip};
        ADDR_MTIME:     gpi_rdata <=  mtime_r2;
        ADDR_MTIMEH:    gpi_rdata <=  mtimeh_r2;
        ADDR_MTIMECMP:  gpi_rdata <=  mtimecmp;
        ADDR_MTIMECMPH: gpi_rdata <=  mtimecmph;
        default:        gpi_rdata <= 32'h0;
      endcase
    end
  end


  // software interrupt
  always @(posedge clk) begin
    if (!rst_n) begin
      msip <= 1'b0;
    end
    else if (gpi_write && gpi_peri_addr == ADDR_MSIP) begin
      msip <= gpi_wdata[0];
    end
  end


  // mtime register write/update
  reg         write_mtime_begin, write_mtime_begin_r1;
  reg         write_mtime_begin_r2, write_mtime_begin_r3;
  reg         write_mtime_end_r1, write_mtime_end_r2;
  reg [31:0]  mtime_wdata, mtime_wdata_r1, mtime_wdata_r2;
  wire        write_mtime = gpi_write && gpi_peri_addr == ADDR_MTIME;

  always @(posedge clk) begin
    if (!rst_n) begin
      write_mtime_begin   <= 1'b0;
    end
    else if (write_mtime) begin
      write_mtime_begin   <= 1'b1;
      mtime_wdata         <= gpi_wdata;
    end
    else if (write_mtime_end_r2) begin
      write_mtime_begin   <= 1'b0;
    end
  end

  always @(posedge clk) begin
    write_mtime_end_r1    <= write_mtime_begin_r2;
    write_mtime_end_r2    <= write_mtime_end_r1;
    mtime_r1              <= mtime;
    mtime_r2              <= mtime_r1;
  end

  always @(posedge clk_rtc) begin
    write_mtime_begin_r1  <= write_mtime_begin;
    write_mtime_begin_r2  <= write_mtime_begin_r1;
    write_mtime_begin_r3  <= write_mtime_begin_r2;
    mtime_wdata_r1        <= mtime_wdata;
    mtime_wdata_r2        <= mtime_wdata_r1;
  end

  always @(posedge clk_rtc) begin
    if (!rst_n) begin
      mtime <= 32'h0;
    end
    else if (write_mtime_begin_r2 && !write_mtime_begin_r3) begin
      mtime <= mtime_wdata_r2;
    end
    else begin
      mtime <= next_mtime;
    end
  end


  // mtimeh register write/update
  reg         write_mtimeh_begin, write_mtimeh_begin_r1;
  reg         write_mtimeh_begin_r2, write_mtimeh_begin_r3;
  reg         write_mtimeh_end_r1, write_mtimeh_end_r2;
  reg [31:0]  mtimeh_wdata, mtimeh_wdata_r1, mtimeh_wdata_r2;
  wire        write_mtimeh = gpi_write && gpi_peri_addr == ADDR_MTIMEH;

  always @(posedge clk) begin
    if (!rst_n) begin
      write_mtimeh_begin  <= 1'b0;
    end
    else if (write_mtimeh) begin
      write_mtimeh_begin  <= 1'b1;
      mtimeh_wdata        <= gpi_wdata;
    end
    else if (write_mtimeh_end_r2) begin
      write_mtimeh_begin  <= 1'b0;
    end
  end

  always @(posedge clk) begin
    write_mtimeh_end_r1   <= write_mtimeh_begin_r2;
    write_mtimeh_end_r2   <= write_mtimeh_end_r1;
    mtimeh_r1             <= mtimeh;
    mtimeh_r2             <= mtimeh_r1;
  end

  always @(posedge clk_rtc) begin
    write_mtimeh_begin_r1 <= write_mtimeh_begin;
    write_mtimeh_begin_r2 <= write_mtimeh_begin_r1;
    write_mtimeh_begin_r3 <= write_mtimeh_begin_r2;
    mtimeh_wdata_r1       <= mtimeh_wdata;
    mtimeh_wdata_r2       <= mtimeh_wdata_r1;
  end

  always @(posedge clk_rtc) begin
    if (!rst_n) begin
      mtimeh <= 32'h0;
    end
    else if (write_mtimeh_begin_r2 && !write_mtimeh_begin_r3) begin
      mtimeh <= mtimeh_wdata_r2;
    end
    else begin
      mtimeh <= next_mtimeh;
    end
  end


  // mtimecmp register write
  always @(posedge clk) begin
    if (!rst_n) begin
      mtimecmp <= 32'h0;
    end
    else if (gpi_write && gpi_peri_addr == ADDR_MTIMECMP) begin
      mtimecmp <= gpi_wdata;
    end
  end

  // mtimecmph register write
  always @(posedge clk) begin
    if (!rst_n) begin
      mtimecmph <= 32'h0;
    end
    else if (gpi_write && gpi_peri_addr == ADDR_MTIMECMPH) begin
      mtimecmph <= gpi_wdata;
    end
  end

endmodule
