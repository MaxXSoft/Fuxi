`timescale 1ns / 1ps

module AXIBridge(
  input           clk,
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
  // general peripheral interface
  output          gpi_read,
  output          gpi_write,
  output  [31:0]  gpi_addr,
  output  [31:0]  gpi_wdata,
  input   [31:0]  gpi_rdata
);

  reg busy, write, r_or_w;

  wire    ar_enter    = axi_arvalid & axi_arready;
  wire    r_retire    = axi_rvalid  & axi_rready & axi_rlast;
  wire    r_valid     = busy & r_or_w & !r_retire;
  wire    aw_enter    = axi_awvalid & axi_awready;
  wire    w_enter     = axi_wvalid  & axi_wready & axi_wlast;
  wire    b_retire    = axi_bvalid  & axi_bready;

  assign  axi_arready = ~busy & (!r_or_w | !axi_awvalid);
  assign  axi_awready = ~busy & ( r_or_w | !axi_arvalid);

  reg [5 :0]  buf_id;
  reg [31:0]  buf_addr;
  reg [7 :0]  buf_len;
  reg [2 :0]  buf_size;

  always @(posedge clk) begin
    if      (!rst_n             ) busy  <= 1'b0;
    else if (ar_enter | aw_enter) busy  <= 1'b1;
    else if (r_retire | b_retire) busy  <= 1'b0;
  end

  always @(posedge clk) begin
    if (!rst_n) begin
      r_or_w    <= 1'b0;
      buf_id    <= 6'b0;
      buf_addr  <= 32'h0;
      buf_len   <= 8'b0;
      buf_size  <= 3'b0;
    end
    else if (ar_enter | aw_enter) begin
      r_or_w    <= ar_enter;
      buf_id    <= ar_enter ? axi_arid   : axi_awid  ;
      buf_addr  <= ar_enter ? axi_araddr : axi_awaddr;
      buf_len   <= ar_enter ? axi_arlen  : axi_awlen ;
      buf_size  <= ar_enter ? axi_arsize : axi_awsize;
    end
  end

  reg wready_reg;
  assign  axi_wready  = wready_reg;
  always @(posedge clk) begin
    if      (!rst_n             ) wready_reg  <= 1'b0;
    else if (aw_enter           ) wready_reg  <= 1'b1;
    else if (w_enter & axi_wlast) wready_reg  <= 1'b0;
  end

  reg rvalid_reg;
  reg rlast_reg;
  assign  axi_rdata   = gpi_rdata;
  assign  axi_rvalid  = rvalid_reg;
  assign  axi_rlast   = rlast_reg;
  always @(posedge clk) begin
    if (!rst_n) begin
      rvalid_reg  <= 1'b0;
      rlast_reg   <= 1'b0;
    end
    else if (r_valid) begin
      rvalid_reg  <= 1'b1;
      rlast_reg   <= 1'b1;
    end
    else if (r_retire) begin
      rvalid_reg  <= 1'b0;
    end
  end

  reg bvalid_reg;
  assign  axi_bvalid  = bvalid_reg;
  always @(posedge clk) begin
    if      (!rst_n   ) bvalid_reg  <= 1'b0;
    else if (w_enter  ) bvalid_reg  <= 1'b1;
    else if (b_retire ) bvalid_reg  <= 1'b0;
  end

  assign  axi_rid     = buf_id;
  assign  axi_bid     = buf_id;
  assign  axi_bresp   = 2'b0;
  assign  axi_rresp   = 2'b0;

  assign  gpi_read    = r_valid;
  assign  gpi_write   = w_enter;
  assign  gpi_addr    = gpi_read ? axi_araddr :
                        gpi_write ? axi_awaddr : 32'h0;
  assign  gpi_wdata   = axi_wdata;

endmodule
