#version 450

layout(location = 0) in vec2 inUv;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D uSampler;

layout(push_constant) uniform PushConsts {
    float uStrength;
} pc;

void main() {
    vec3 c = texture(uSampler, inUv).rgb * pc.uStrength;
    float d = fract(sin(dot(inUv * vec2(1234.5, 987.6),
        vec2(12.9898, 78.233))) * 43758.5453);
    outColor = vec4(c + (d - 0.5) * (1.5 / 255.0), 1.0);
}
