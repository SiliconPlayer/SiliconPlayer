#version 450

layout(location = 0) in float inAlpha;
layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConsts {
    vec3 uColor;
    float uSoft;
    float uGlobalAlpha;
    float uSquare;
    vec2 uResolution;
} pc;

void main() {
    if (pc.uSquare > 0.5) {
        outColor = vec4(pc.uColor, inAlpha * pc.uGlobalAlpha);
        return;
    }
    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) discard;
    float edge = mix(0.12, 0.9, pc.uSoft);
    float a = 1.0 - smoothstep(1.0 - edge, 1.0, d);
    float core = 1.0 - smoothstep(0.0, mix(0.25, 0.9, pc.uSoft), d);
    a = max(a * 0.85, core);
    outColor = vec4(pc.uColor, a * inAlpha * pc.uGlobalAlpha);
}
