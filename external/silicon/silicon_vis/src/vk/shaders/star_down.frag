#version 450

layout(location = 0) in vec2 inUv;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D uSampler;

layout(push_constant) uniform PushConsts {
    float uThresh;
} pc;

void main() {
    vec3 c = texture(uSampler, inUv).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    float k = smoothstep(pc.uThresh, pc.uThresh + 0.25, l);
    outColor = vec4(c * k * 2.0, 1.0);
}
