`timescale 1ns/1ps

module gpio_axi_tb;
  reg clk = 0;
  always #5 clk = ~clk;
  reg rst_n = 0;
  reg [31:0] araddr = 0;
  reg [5:0] arid = 0;
  reg arvalid = 0, rready = 0;
  reg [7:0] switches = 8'h12;
  wire arready, rvalid, rlast;
  wire [5:0] rid;
  wire [31:0] rdata;
  wire [1:0] rresp;

  confreg dut(
    .aclk(clk), .timer_clk(clk), .aresetn(rst_n),
    .arid(arid), .araddr(araddr), .arlen(8'd0), .arsize(3'd2),
    .arburst(2'd1), .arlock(2'd0), .arcache(4'd0), .arprot(3'd0),
    .arvalid(arvalid), .arready(arready), .rid(rid), .rdata(rdata),
    .rresp(rresp), .rlast(rlast), .rvalid(rvalid), .rready(rready),
    .awid(6'd0), .awaddr(32'd0), .awlen(8'd0), .awsize(3'd2),
    .awburst(2'd1), .awlock(2'd0), .awcache(4'd0), .awprot(3'd0),
    .awvalid(1'b0), .awready(), .wid(6'd0), .wdata(32'd0),
    .wstrb(4'd0), .wlast(1'b1), .wvalid(1'b0), .wready(),
    .bid(), .bresp(), .bvalid(), .bready(1'b1),
    .ram_random_mask(), .led(), .led_rg0(), .led_rg1(),
    .num_csn(), .num_a_g(), .switch(switches), .btn_key_col(),
    .btn_key_row(4'hf), .btn_step(2'b11), .user_cr0(), .user_cr1()
  );

  task automatic tick;
    @(posedge clk); #1;
  endtask

  task automatic read_switch(input [7:0] expected, input integer stall_cycles);
    @(negedge clk);
    araddr = 32'hbfaff020;
    arid = 6'h21;
    arvalid = 1;
    while (!arready) tick();
    tick();
    arvalid = 0;
    araddr = 0;
    arid = 0;
    while (!rvalid) tick();
    if (rdata != {24'd0, expected}) $fatal(1, "GPIO returned wrong initial switch value");
    repeat (stall_cycles) begin
      switches = ~switches;
      tick();
      if (!rvalid || !rlast || rid != 6'h21 || rresp != 0 || rdata != {24'd0, expected})
        $fatal(1, "GPIO read response changed during backpressure");
    end
    @(negedge clk);
    rready = 1;
    tick();
    rready = 0;
  endtask

  initial begin
    repeat (3) tick();
    rst_n = 1;
    read_switch(8'h12, 6);
    switches = 8'ha5;
    read_switch(8'ha5, 0);
    $display("GPIO AXI regression passed");
    $finish;
  end

  initial begin
    #20000;
    $fatal(1, "GPIO AXI regression timed out");
  end
endmodule
