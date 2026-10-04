#version 450

layout(location = 0) in vec2 inUv;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D uSampler;

layout(push_constant) uniform PushConsts {
    float uAlpha;
} pc;

void main() {
    vec4 t = texture(uSampler, inUv);
    outColor = vec4(t.rgb, t.a * pc.uAlpha);
}
