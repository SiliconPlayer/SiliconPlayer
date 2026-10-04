#version 450

layout(location = 0) in vec2 inNdc;
layout(location = 0) out vec2 outUv;

void main() {
    outUv = inNdc * 0.5 + 0.5;
    gl_Position = vec4(inNdc, 0.0, 1.0);
}
