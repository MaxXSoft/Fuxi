// from https://github.com/trivialmips/nontrivial-mips
// modified by: MaxXing, 2020-05

`ifndef FUXISOC_IOBUF_V_
`define FUXISOC_IOBUF_V_

`define IOBUF_GEN(IN, OUT)          \
    wire OUT``_i, OUT``_o, OUT``_t; \
    IOBUF IN``_buf (                \
      .IO(IN),                      \
      .I(OUT``_o),                  \
      .O(OUT``_i),                  \
      .T(OUT``_t)                   \
    );

`define IOBUF_GEN_SIMPLE(IN) `IOBUF_GEN(IN, IN)

`define IOBUF_GEN_VEC(BITS, IN, OUT)                            \
    wire [BITS - 1:0] OUT``_i, OUT``_o, OUT``_t;                \
    genvar IN``_gen_var;                                        \
    generate                                                    \
      for (IN``_gen_var = 0; IN``_gen_var < BITS;               \
           IN``_gen_var = IN``_gen_var + 1) begin: IN``_buf_gen \
        IOBUF IN``_buf (                                        \
          .IO(IN[IN``_gen_var]),                                \
          .O(OUT``_i[IN``_gen_var]),                            \
          .I(OUT``_o[IN``_gen_var]),                            \
          .T(OUT``_t[IN``_gen_var])                             \
        );                                                      \
      end                                                       \
    endgenerate

`define IOBUF_GEN_VEC_SIMPLE(BITS, IN) `IOBUF_GEN_VEC(BITS, IN, IN)

`endif  // FUXISOC_IOBUF_V_
