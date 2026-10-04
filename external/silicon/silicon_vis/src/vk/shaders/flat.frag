#version 450

layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConsts {
    vec2 uResolution;
    vec4 uColor;
} pc;

void main() {
    outColor = pc.uColor;
}
