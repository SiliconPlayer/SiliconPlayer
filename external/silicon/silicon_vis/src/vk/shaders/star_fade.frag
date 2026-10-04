#version 450

layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConsts {
    vec4 uColor;
    vec2 uResolution;
} pc;

void main() {
    outColor = vec4(0.0, 0.0, 0.0, pc.uColor.a);
}
