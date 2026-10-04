#version 450

layout(location = 0) in vec2 inTexCoord;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D uSampler;

layout(push_constant) uniform PushConsts {
    vec2 uResolution;
    float uOffsetX;
    float uAlpha;
} pc;

void main() {
    vec4 tex = texture(uSampler, inTexCoord);
    outColor = vec4(tex.rgb, tex.a * pc.uAlpha);
}
