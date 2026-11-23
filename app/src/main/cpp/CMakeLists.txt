cmake_minimum_required(VERSION 3.22.1)
project(clawdroid_native C)

# Termux の libtermux.so（4KB 境界）の代わり。詳しくは termux_jni.c
add_library(termux SHARED termux_jni.c)
target_link_options(termux PRIVATE "-Wl,-z,max-page-size=16384")
