cmake_minimum_required(VERSION 3.22.1)

project(silicon_vis LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(SILICON_VIS_SOURCES
    src/vis_pipeline.cpp
    src/vis_api.cpp
    src/gl/gl_program.cpp
    src/gl/gl_primitives.cpp
    src/gl/gl_font_atlas.cpp
    src/gl/gl_artwork_renderer.cpp
    src/renderers/channel_scope_renderer.cpp
    src/renderers/oscilloscope_renderer.cpp
    src/renderers/bars_renderer.cpp
    src/renderers/vu_meters_renderer.cpp
)

if (ANDROID)
    list(APPEND SILICON_VIS_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../app/src/main/cpp/SiliconVisJni.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../app/src/main/cpp/ProjectMVisualizer.cpp
    )
endif()

add_library(silicon_vis SHARED ${SILICON_VIS_SOURCES})

target_include_directories(silicon_vis
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
)

if (ANDROID)
    add_library(libprojectM-4 SHARED IMPORTED)
    set_target_properties(libprojectM-4 PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../app/src/main/cpp/prebuilt/${ANDROID_ABI}/lib/libprojectM-4.so)

    target_link_libraries(silicon_vis
        PUBLIC
            GLESv2
            EGL
            log
            m
            libprojectM-4
    )
    target_compile_options(silicon_vis PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden
    )
    target_link_options(silicon_vis PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )
else()
    # Desktop / Qt / Linux / Windows / macOS
    find_package(OpenGL REQUIRED)
    target_link_libraries(silicon_vis
        PUBLIC
            OpenGL::GL
            m
    )
endif()

