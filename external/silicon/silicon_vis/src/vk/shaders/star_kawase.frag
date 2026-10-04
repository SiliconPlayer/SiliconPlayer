#version 450

layout(location = 0) in vec2 inUv;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D uSampler;

layout(push_constant) uniform PushConsts {
    vec2 uPx;
} pc;

void main() {
    vec3 c = texture(uSampler, inUv).rgb * 0.2;
    c += texture(uSampler, inUv + vec2(-1.0, -1.0) * pc.uPx).rgb * 0.2;
    c += texture(uSampler, inUv + vec2( 1.0, -1.0) * pc.uPx).rgb * 0.2;
    c += texture(uSampler, inUv + vec2(-1.0,  1.0) * pc.uPx).rgb * 0.2;
    c += texture(uSampler, inUv + vec2( 1.0,  1.0) * pc.uPx).rgb * 0.2;
    outColor = vec4(c, 1.0);
}
