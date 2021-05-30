BINARY     := kila
KERNEL      := linux-2.6.17
ARCH        := x86
C_FLAGS     := -Wall
KMOD_DIR    := $(shell pwd)
TARGET_PATH := /lib/modules/$(shell uname -r)/kernel/drivers/char

OBJECTS := main.o ikcp.o

ccflags-y += $(C_FLAGS)

obj-m += $(BINARY).o

$(BINARY)-y := $(OBJECTS)

$(BINARY).ko:
	    make -C $(KERNEL) M=$(KMOD_DIR) modules

install:
	    cp $(BINARY).ko $(TARGET_PATH)
	        depmod -a

uninstall:
	    rm $(TARGET_PATH)/$(BINARY).ko
	        depmod -a

clean:
	    make -C $(KERNEL) M=$(KMOD_DIR) clean
