# CMAKE generated file: DO NOT EDIT!
# Generated by "Unix Makefiles" Generator, CMake Version 3.10

# Delete rule output on recipe failure.
.DELETE_ON_ERROR:


#=============================================================================
# Special targets provided by cmake.

# Disable implicit rules so canonical targets will work.
.SUFFIXES:


# Remove some rules from gmake that .SUFFIXES does not remove.
SUFFIXES =

.SUFFIXES: .hpux_make_needs_suffix_list


# Suppress display of executed commands.
$(VERBOSE).SILENT:


# A target that is always out of date.
cmake_force:

.PHONY : cmake_force

#=============================================================================
# Set environment variables for the build.

# The shell in which to execute make rules.
SHELL = /bin/sh

# The CMake executable.
CMAKE_COMMAND = /usr/bin/cmake

# The command to remove a file.
RM = /usr/bin/cmake -E remove -f

# Escaping for special characters.
EQUALS = =

# The top-level source directory on which CMake was run.
CMAKE_SOURCE_DIR = /home/wooyme/Projects/Wsocks/wsocks/jni

# The top-level build directory on which CMake was run.
CMAKE_BINARY_DIR = /home/wooyme/Projects/Wsocks/wsocks/jni

# Include any dependencies generated for this target.
include CMakeFiles/Udp.dir/depend.make

# Include the progress variables for this target.
include CMakeFiles/Udp.dir/progress.make

# Include the compile flags for this target's objects.
include CMakeFiles/Udp.dir/flags.make

CMakeFiles/Udp.dir/udp.c.o: CMakeFiles/Udp.dir/flags.make
CMakeFiles/Udp.dir/udp.c.o: udp.c
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --progress-dir=/home/wooyme/Projects/Wsocks/wsocks/jni/CMakeFiles --progress-num=$(CMAKE_PROGRESS_1) "Building C object CMakeFiles/Udp.dir/udp.c.o"
	/usr/bin/cc $(C_DEFINES) $(C_INCLUDES) $(C_FLAGS) -o CMakeFiles/Udp.dir/udp.c.o   -c /home/wooyme/Projects/Wsocks/wsocks/jni/udp.c

CMakeFiles/Udp.dir/udp.c.i: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Preprocessing C source to CMakeFiles/Udp.dir/udp.c.i"
	/usr/bin/cc $(C_DEFINES) $(C_INCLUDES) $(C_FLAGS) -E /home/wooyme/Projects/Wsocks/wsocks/jni/udp.c > CMakeFiles/Udp.dir/udp.c.i

CMakeFiles/Udp.dir/udp.c.s: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Compiling C source to assembly CMakeFiles/Udp.dir/udp.c.s"
	/usr/bin/cc $(C_DEFINES) $(C_INCLUDES) $(C_FLAGS) -S /home/wooyme/Projects/Wsocks/wsocks/jni/udp.c -o CMakeFiles/Udp.dir/udp.c.s

CMakeFiles/Udp.dir/udp.c.o.requires:

.PHONY : CMakeFiles/Udp.dir/udp.c.o.requires

CMakeFiles/Udp.dir/udp.c.o.provides: CMakeFiles/Udp.dir/udp.c.o.requires
	$(MAKE) -f CMakeFiles/Udp.dir/build.make CMakeFiles/Udp.dir/udp.c.o.provides.build
.PHONY : CMakeFiles/Udp.dir/udp.c.o.provides

CMakeFiles/Udp.dir/udp.c.o.provides.build: CMakeFiles/Udp.dir/udp.c.o


CMakeFiles/Udp.dir/kcp_jni.c.o: CMakeFiles/Udp.dir/flags.make
CMakeFiles/Udp.dir/kcp_jni.c.o: kcp_jni.c
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --progress-dir=/home/wooyme/Projects/Wsocks/wsocks/jni/CMakeFiles --progress-num=$(CMAKE_PROGRESS_2) "Building C object CMakeFiles/Udp.dir/kcp_jni.c.o"
	/usr/bin/cc $(C_DEFINES) $(C_INCLUDES) $(C_FLAGS) -o CMakeFiles/Udp.dir/kcp_jni.c.o   -c /home/wooyme/Projects/Wsocks/wsocks/jni/kcp_jni.c

CMakeFiles/Udp.dir/kcp_jni.c.i: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Preprocessing C source to CMakeFiles/Udp.dir/kcp_jni.c.i"
	/usr/bin/cc $(C_DEFINES) $(C_INCLUDES) $(C_FLAGS) -E /home/wooyme/Projects/Wsocks/wsocks/jni/kcp_jni.c > CMakeFiles/Udp.dir/kcp_jni.c.i

CMakeFiles/Udp.dir/kcp_jni.c.s: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Compiling C source to assembly CMakeFiles/Udp.dir/kcp_jni.c.s"
	/usr/bin/cc $(C_DEFINES) $(C_INCLUDES) $(C_FLAGS) -S /home/wooyme/Projects/Wsocks/wsocks/jni/kcp_jni.c -o CMakeFiles/Udp.dir/kcp_jni.c.s

CMakeFiles/Udp.dir/kcp_jni.c.o.requires:

.PHONY : CMakeFiles/Udp.dir/kcp_jni.c.o.requires

CMakeFiles/Udp.dir/kcp_jni.c.o.provides: CMakeFiles/Udp.dir/kcp_jni.c.o.requires
	$(MAKE) -f CMakeFiles/Udp.dir/build.make CMakeFiles/Udp.dir/kcp_jni.c.o.provides.build
.PHONY : CMakeFiles/Udp.dir/kcp_jni.c.o.provides

CMakeFiles/Udp.dir/kcp_jni.c.o.provides.build: CMakeFiles/Udp.dir/kcp_jni.c.o


# Object files for target Udp
Udp_OBJECTS = \
"CMakeFiles/Udp.dir/udp.c.o" \
"CMakeFiles/Udp.dir/kcp_jni.c.o"

# External object files for target Udp
Udp_EXTERNAL_OBJECTS =

libUdp.so: CMakeFiles/Udp.dir/udp.c.o
libUdp.so: CMakeFiles/Udp.dir/kcp_jni.c.o
libUdp.so: CMakeFiles/Udp.dir/build.make
libUdp.so: CMakeFiles/Udp.dir/link.txt
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --bold --progress-dir=/home/wooyme/Projects/Wsocks/wsocks/jni/CMakeFiles --progress-num=$(CMAKE_PROGRESS_3) "Linking C shared library libUdp.so"
	$(CMAKE_COMMAND) -E cmake_link_script CMakeFiles/Udp.dir/link.txt --verbose=$(VERBOSE)

# Rule to build all files generated by this target.
CMakeFiles/Udp.dir/build: libUdp.so

.PHONY : CMakeFiles/Udp.dir/build

CMakeFiles/Udp.dir/requires: CMakeFiles/Udp.dir/udp.c.o.requires
CMakeFiles/Udp.dir/requires: CMakeFiles/Udp.dir/kcp_jni.c.o.requires

.PHONY : CMakeFiles/Udp.dir/requires

CMakeFiles/Udp.dir/clean:
	$(CMAKE_COMMAND) -P CMakeFiles/Udp.dir/cmake_clean.cmake
.PHONY : CMakeFiles/Udp.dir/clean

CMakeFiles/Udp.dir/depend:
	cd /home/wooyme/Projects/Wsocks/wsocks/jni && $(CMAKE_COMMAND) -E cmake_depends "Unix Makefiles" /home/wooyme/Projects/Wsocks/wsocks/jni /home/wooyme/Projects/Wsocks/wsocks/jni /home/wooyme/Projects/Wsocks/wsocks/jni /home/wooyme/Projects/Wsocks/wsocks/jni /home/wooyme/Projects/Wsocks/wsocks/jni/CMakeFiles/Udp.dir/DependInfo.cmake --color=$(COLOR)
.PHONY : CMakeFiles/Udp.dir/depend
