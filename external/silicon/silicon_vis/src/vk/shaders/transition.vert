#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inTexCoord;

layout(location = 0) out vec2 outTexCoord;

layout(push_constant) uniform PushConsts {
    vec2 uResolution;
    float uOffsetX;
    float uAlpha;
} pc;

void main() {
    outTexCoord = inTexCoord;
    vec2 pos = inPosition + vec2(pc.uOffsetX, 0.0);
    vec2 zeroToOne = pos / pc.uResolution;
    vec2 clipSpace = zeroToOne * 2.0 - 1.0;
    gl_Position = vec4(clipSpace.x, clipSpace.y, 0.0, 1.0);
}
