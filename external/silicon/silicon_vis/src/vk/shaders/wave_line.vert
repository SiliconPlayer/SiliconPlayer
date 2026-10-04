#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in float inDist;
layout(location = 0) out float outDist;

layout(push_constant) uniform PushConsts {
    vec2 uResolution;
    vec4 uColor;
    float uHalfWidth;
    float uSoftness;
} pc;

void main() {
    vec2 zeroToOne = inPosition / pc.uResolution;
    vec2 clipSpace = zeroToOne * 2.0 - 1.0;
    gl_Position = vec4(clipSpace.x, clipSpace.y, 0.0, 1.0);
    outDist = inDist;
}
