# directories
export TOP_DIR = $(shell if [ "$$PWD" != "" ]; then echo $$PWD; else pwd; fi)
export TARGET_DIR = $(TOP_DIR)/verilog/build
export SRC_DIR = $(TOP_DIR)/src/main/scala
export TEST_DIR = $(TOP_DIR)/src/test/scala

# files
FUXI_SRC := $(wildcard $(SRC_DIR)/*.scala)
FUXI_SRC += $(wildcard $(SRC_DIR)/**/*.scala)
FUXI_TARGET := $(TARGET_DIR)/Fuxi.v

.PHONY: all clean

all: $(FUXI_TARGET)

$(FUXI_TARGET): $(TARGET_DIR) $(FUXI_SRC)
	sbt "runMain Fuxi --target-dir $(TARGET_DIR)"

$(TARGET_DIR):
	mkdir -p $(TARGET_DIR)

clean:
	-rm -f $(TARGET_DIR)/*
