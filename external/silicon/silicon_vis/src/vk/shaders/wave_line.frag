#version 450

layout(location = 0) in float inDist;
layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConsts {
    vec4 uColor;
    vec2 uResolution;
    float uHalfWidth;
    float uSoftness;
} pc;

void main() {
    float d = abs(inDist);
    float inner = max(pc.uHalfWidth - pc.uSoftness, 0.0);
    float outer = pc.uHalfWidth + pc.uSoftness;
    float core = 1.0 - smoothstep(inner, outer, d);
    outColor = vec4(pc.uColor.rgb, pc.uColor.a * core);
}
