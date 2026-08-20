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

add_library(silicon_vis STATIC ${SILICON_VIS_SOURCES})

target_include_directories(silicon_vis
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
)

if (ANDROID)
    target_link_libraries(silicon_vis
        PUBLIC
            GLESv2
            log
            m
    )
    target_compile_options(silicon_vis PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden
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
