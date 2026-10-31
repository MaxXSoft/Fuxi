# Configuration.
CONFIG ?= DefaultConfig

# Directories.
export TOP_DIR = $(shell pwd)
export TARGET_DIR = $(TOP_DIR)/verilog/build
export SRC_DIR = $(TOP_DIR)/src/main/scala
export TEST_DIR = $(TOP_DIR)/src/test/scala

# Files.
FUXI_SRC := $(wildcard $(SRC_DIR)/*.scala)
FUXI_SRC += $(wildcard $(SRC_DIR)/**/*.scala)
FUXI_TARGET := $(TARGET_DIR)/Fuxi.v
FUXI_CONFIG := $(TARGET_DIR)/.config

.PHONY: all clean FORCE

all: $(FUXI_TARGET)

# The output path is shared, so changing presets must invalidate the RTL.
ifneq ($(shell cat $(FUXI_CONFIG) 2>/dev/null),$(CONFIG))
$(FUXI_TARGET): FORCE
endif

$(FUXI_TARGET): $(FUXI_SRC) | $(TARGET_DIR)
	sbt "runMain Fuxi --config $(CONFIG) --target-dir $(TARGET_DIR)"
	@printf '%s\n' "$(CONFIG)" > $(FUXI_CONFIG)

$(TARGET_DIR):
	mkdir -p $(TARGET_DIR)

clean:
	-rm -f $(TARGET_DIR)/* $(FUXI_CONFIG)
