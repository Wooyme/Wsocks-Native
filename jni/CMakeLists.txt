cmake_minimum_required(VERSION 3.10)
project(Udp C)
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})
set(CMAKE_C_STANDARD 99)
set(INCLUDE_FILES udp.h
        co_zzyun_wsocks_UdpUtil.h)
set(SOURCE_FILES
        udp.c
        kcp_jni.c)
source_group(include FILES ${INCLUDE_FILES})
source_group(source FILES ${SOURCE_FILES})
add_library(Udp SHARED ${INCLUDE_FILES} ${SOURCE_FILES})
target_link_libraries (Udp ${CMAKE_THREAD_LIBS_INIT})
