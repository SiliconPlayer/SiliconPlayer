#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in float inSize;
layout(location = 2) in float inAlpha;

layout(location = 0) out float outAlpha;

layout(push_constant) uniform PushConsts {
    vec3 uColor;
    float uSoft;
    float uGlobalAlpha;
    float uSquare;
    vec2 uResolution;
} pc;

void main() {
    vec2 zeroToOne = inPosition / pc.uResolution;
    vec2 clipSpace = zeroToOne * 2.0 - 1.0;
    gl_Position = vec4(clipSpace.x, clipSpace.y, 0.0, 1.0);
    gl_PointSize = inSize;
    outAlpha = inAlpha;
}
