#version 450

layout(location = 0) in vec2 inTexCoord;
layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConsts {
    vec4 uScrimColor;
    vec2 uResolution;
    int uMode;
    float uPad;
} pc;

void main() {
    float alpha = 0.0;
    float y = inTexCoord.y;

    if (pc.uMode == 1) { // Bars
        float curve = y * y * (3.0 - 2.0 * y);
        alpha = mix(0.18, 0.58, curve);
    } else if (pc.uMode == 2) { // Oscilloscope Mono
        float distFromCenter = abs(y - 0.5) * 2.0;
        alpha = mix(0.48, 0.12, distFromCenter);
    } else if (pc.uMode == 3) { // Oscilloscope Stereo
        float dist1 = abs(y - 0.25) * 4.0;
        float dist2 = abs(y - 0.75) * 4.0;
        float band = min(dist1, dist2);
        alpha = mix(0.48, 0.12, clamp(band, 0.0, 1.0));
    } else if (pc.uMode == 4) { // VU Meters Top
        float topDist = y / 0.35;
        alpha = mix(0.55, 0.0, clamp(topDist, 0.0, 1.0));
    } else if (pc.uMode == 5) { // VU Meters Bottom
        float btmDist = (1.0 - y) / 0.35;
        alpha = mix(0.55, 0.0, clamp(btmDist, 0.0, 1.0));
    } else if (pc.uMode == 6) { // Channel Scope
        alpha = 0.28;
    } else if (pc.uMode == 7) { // Starfield
        alpha = 0.32;
    }

    outColor = vec4(pc.uScrimColor.rgb, pc.uScrimColor.a * alpha);
}
