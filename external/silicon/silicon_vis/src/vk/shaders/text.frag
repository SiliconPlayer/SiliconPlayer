#version 450

layout(location = 0) in vec2 inTexCoord;
layout(location = 1) in vec4 inColor;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D uFontSampler;

void main() {
    float alpha = texture(uFontSampler, inTexCoord).a;
    outColor = vec4(inColor.rgb, inColor.a * alpha);
}
