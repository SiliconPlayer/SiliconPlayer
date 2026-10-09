cmake_minimum_required(VERSION 3.16)
project(nezplugpp C)

add_library(nezplugpp SHARED
    common/unix/csounix.c
    cpu/kmz80/kmdmg.c
    cpu/kmz80/kmevent.c
    cpu/kmz80/kmr800.c
    cpu/kmz80/kmz80.c
    cpu/kmz80/kmz80c.c
    cpu/kmz80/kmz80t.c
    device/s_dmg.c
    device/s_hes.c
    device/s_hesad.c
    device/s_logtbl.c
    device/s_psg.c
    device/s_scc.c
    device/s_sng.c
    device/nes/logtable.c
    device/nes/s_apu.c
    device/nes/s_fds.c
    device/nes/s_fds1.c
    device/nes/s_fds2.c
    device/nes/s_fds3.c
    # s_fdse.c drives an external FDS plugin through a Win32 DLL; its only
    # call site in s_fds.c is already disabled with #if 0.
    device/nes/s_fme7.c
    device/nes/s_mmc5.c
    device/nes/s_n106.c
    device/nes/s_vrc6.c
    device/nes/s_vrc7.c
    device/opl/s_deltat.c
    device/opl/s_opl.c
    device/opl/s_opltbl.c
    format/audiosys.c
    format/handler.c
    format/m_gbr.c
    format/m_hes.c
    format/m_kss.c
    format/m_nsd.c
    format/m_nsf.c
    format/m_sgc.c
    format/m_zxay.c
    format/nezplug.c
    format/nsf6502.c
    format/songinfo.c
)

target_include_directories(nezplugpp PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/format>
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/device>
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/cpu>
    $<INSTALL_INTERFACE:include/nezplugpp>
)
set_target_properties(nezplugpp PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    C_STANDARD 99
)
# Globals live as tentative definitions in shared headers; -fcommon restores
# the historical merge behavior newer compilers dropped by default.
target_compile_options(nezplugpp PRIVATE -fcommon)
# 2007-era code assigns concretely-typed functions to void*-generic handler
# slots; newer compilers promote that to an error. Demote back to a warning.
if(CMAKE_C_COMPILER_ID STREQUAL "GNU")
    target_compile_options(nezplugpp PRIVATE -Wno-error=incompatible-pointer-types)
elseif(CMAKE_C_COMPILER_ID MATCHES "Clang")
    target_compile_options(nezplugpp PRIVATE -Wno-error=incompatible-function-pointer-types)
endif()

install(TARGETS nezplugpp
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)
install(FILES nezplug.h nestypes.h neserr.h DESTINATION include/nezplugpp)
install(DIRECTORY format device cpu common
    DESTINATION include/nezplugpp
    FILES_MATCHING PATTERN "*.h"
    PATTERN "win32" EXCLUDE
    PATTERN "nsfsdk" EXCLUDE
    PATTERN "test" EXCLUDE
)
