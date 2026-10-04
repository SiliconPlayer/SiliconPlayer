#version 450

layout(location = 0) in vec2 vPosition;
layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConsts {
    vec4 uCenterColor;
    vec4 uEdgeColor;
    vec4 uCircleColor;
    vec2 uResolution;
    float uCircleRadius;
    float uAlpha;
} pc;

void main() {
    vec2 center = pc.uResolution * 0.5;
    float maxDist = length(center);
    float dist = distance(vPosition, center);
    float t = clamp(dist / max(maxDist, 1.0), 0.0, 1.0);
    vec4 bg = mix(pc.uCenterColor, pc.uEdgeColor, t * t);

    float alpha = clamp(pc.uCircleRadius - dist + 0.5, 0.0, 1.0) * pc.uCircleColor.a;
    vec4 col = mix(bg, vec4(pc.uCircleColor.rgb, 1.0), alpha);
    outColor = vec4(col.rgb, col.a * pc.uAlpha);
}
