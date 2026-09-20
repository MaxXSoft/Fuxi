`timescale 1ns/1ps

module clint_axi_tb;
  reg clk = 0;
  reg clk_rtc = 0;
  always #5 clk = ~clk;
  always #7 clk_rtc = ~clk_rtc;
  reg rst_n = 0;
  reg [5:0] arid = 0, awid = 0;
  reg [31:0] araddr = 0, awaddr = 0, wdata = 0;
  reg arvalid = 0, awvalid = 0, wvalid = 0, rready = 0, bready = 0;
  wire arready, awready, wready, rvalid, rlast, bvalid;
  wire [5:0] rid, bid;
  wire [1:0] rresp, bresp;
  wire [31:0] rdata;
  wire intr_timer, intr_soft;
  integer failures = 0;

  Clint dut(
    .clk(clk), .clk_rtc(clk_rtc), .rst_n(rst_n),
    .axi_arid(arid), .axi_araddr(araddr), .axi_arlen(8'd0),
    .axi_arsize(3'd2), .axi_arburst(2'd1), .axi_arlock(2'd0),
    .axi_arcache(4'd0), .axi_arprot(3'd0),
    .axi_arvalid(arvalid), .axi_arready(arready),
    .axi_rid(rid), .axi_rdata(rdata), .axi_rresp(rresp),
    .axi_rlast(rlast), .axi_rvalid(rvalid), .axi_rready(rready),
    .axi_awid(awid), .axi_awaddr(awaddr), .axi_awlen(8'd0),
    .axi_awsize(3'd2), .axi_awburst(2'd1), .axi_awlock(2'd0),
    .axi_awcache(4'd0), .axi_awprot(3'd0),
    .axi_awvalid(awvalid), .axi_awready(awready),
    .axi_wid(awid), .axi_wdata(wdata), .axi_wstrb(4'hf),
    .axi_wlast(1'b1), .axi_wvalid(wvalid), .axi_wready(wready),
    .axi_bid(bid), .axi_bresp(bresp), .axi_bvalid(bvalid),
    .axi_bready(bready), .intr_timer(intr_timer), .intr_soft(intr_soft)
  );

  task automatic tick;
    @(posedge clk); #1;
  endtask

  task automatic check(input bit condition, input string message);
    if (!condition) begin
      $error("%s", message);
      failures++;
    end
  endtask

  task automatic write_reg(input [31:0] address, input [31:0] value);
    @(negedge clk);
    awid = 6'h15;
    awaddr = address;
    awvalid = 1;
    do tick(); while (!wready);
    // AW has handshaken; its address and ID no longer belong to this transfer.
    awvalid = 0;
    awaddr = 32'h11000000;
    awid = 6'h2a;
    repeat (3) tick(); // legal delay between address and write data
    @(negedge clk);
    wdata = value;
    wvalid = 1;
    tick();
    wvalid = 0;
    while (!bvalid) tick();
    check(bid == 6'h15 && bresp == 0, "write response must preserve the accepted ID");
    @(negedge clk);
    bready = 1;
    tick();
    bready = 0;
  endtask

  task automatic read_reg(input [31:0] address, output [31:0] value);
    @(negedge clk);
    arid = 6'h09;
    araddr = address;
    arvalid = 1;
    while (!arready) tick();
    tick();
    arvalid = 0;
    araddr = 32'h11000000;
    arid = 6'h3f;
    while (!rvalid) tick();
    check(rid == 6'h09 && rresp == 0 && rlast,
          "read response must preserve the accepted ID and single-beat framing");
    value = rdata;
    @(negedge clk);
    rready = 1;
    tick();
    rready = 0;
  endtask

  reg [31:0] value;
  initial begin
    repeat (4) tick();
    rst_n = 1;
    write_reg(32'h11000108, 32'h13579bdf);
    check(!intr_soft, "changing AWADDR after handshake must not write MSIP");
    read_reg(32'h11000108, value);
    check(value == 32'h13579bdf, "MTIMECMP read/write must use the accepted address");
    write_reg(32'h1100010c, 32'h2468ace0);
    read_reg(32'h1100010c, value);
    check(value == 32'h2468ace0, "second CLINT register must not alias the changed address");
    if (failures != 0) $fatal(1, "CLINT AXI regression: %0d failures", failures);
    $display("CLINT AXI regression passed");
    $finish;
  end

  initial begin
    #20000;
    $fatal(1, "CLINT AXI regression timed out");
  end
endmodule
