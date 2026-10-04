#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inTexCoord;

layout(location = 0) out vec2 outTexCoord;

layout(push_constant) uniform PushConsts {
    vec4 uColor;
    vec2 uResolution;
    float uMono;
    float uPad;
} pc;

void main() {
    outTexCoord = inTexCoord;
    vec2 zeroToOne = inPosition / pc.uResolution;
    vec2 clipSpace = zeroToOne * 2.0 - 1.0;
    gl_Position = vec4(clipSpace.x, clipSpace.y, 0.0, 1.0);
}
