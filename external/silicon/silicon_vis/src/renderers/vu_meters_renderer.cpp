#include "vu_meters_renderer.h"
#include "vk/vk_primitives.h"
#include "vk/vk_dispatch.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace silicon::vis {

VuMetersRenderer::VuMetersRenderer() = default;

bool VuMetersRenderer::initGl() {
    if (!flatRenderer_.init()) return false;
    if (!fontAtlas_.init()) return false;
    if (!textProgram_.init()) return false;
    return true;
}

void VuMetersRenderer::releaseGl() {
    flatRenderer_.release();
    fontAtlas_.release();
    textProgram_.release();
}

void VuMetersRenderer::resize(int32_t widthPx, int32_t heightPx, float density) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    density_ = std::max(1.0f, density);
}

void VuMetersRenderer::setVuLevels(float left, float right) {
    const float l = std::clamp(left, 0.0f, 1.0f);
    const float r = std::clamp(right, 0.0f, 1.0f);
    if (smoothing_ > 0.0f) {
        leftPeak_ = (leftPeak_ * smoothing_) + (l * (1.0f - smoothing_));
        rightPeak_ = (rightPeak_ * smoothing_) + (r * (1.0f - smoothing_));
    } else {
        leftPeak_ = l;
        rightPeak_ = r;
    }
}

void VuMetersRenderer::pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) {
    if (!pcmInterleaved || frames <= 0) return;

    float maxL = 0.0f;
    float maxR = 0.0f;

    if (channels >= 2) {
        for (int i = 0; i < frames; ++i) {
            float l = std::abs(pcmInterleaved[i * 2]);
            float r = std::abs(pcmInterleaved[i * 2 + 1]);
            if (l > maxL) maxL = l;
            if (r > maxR) maxR = r;
        }
    } else {
        for (int i = 0; i < frames; ++i) {
            float m = std::abs(pcmInterleaved[i]);
            if (m > maxL) maxL = m;
        }
        maxR = maxL;
    }

    // Smooth peak decay
    leftPeak_ = (maxL > leftPeak_) ? maxL : (leftPeak_ * 0.90f + maxL * 0.10f);
    rightPeak_ = (maxR > rightPeak_) ? maxR : (rightPeak_ * 0.90f + maxR * 0.10f);
}

void VuMetersRenderer::setOptions(
    bool stereo,
    int anchor,
    float smoothing,
    uint32_t fillColorArgb,
    uint32_t trackColorArgb,
    uint32_t labelColorArgb
) {
    stereo_ = stereo;
    anchor_ = anchor;
    smoothing_ = std::clamp(smoothing, 0.0f, 0.98f);
    fillColorArgb_ = fillColorArgb;
    trackColorArgb_ = trackColorArgb;
    labelColorArgb_ = labelColorArgb;
}

static float computeVuDbLevel(float raw) {
    // Double-wide libm: float log10 resolves past Debian-stable glibc.
    float db = 20.0f * std::log10(static_cast<double>(std::max(raw, 0.0001f)));
    float dbFloor = -58.0f;
    float norm = std::clamp((db - dbFloor) / -dbFloor, 0.0f, 1.0f);
    return std::clamp(std::pow(norm, 0.62f), 0.0f, 1.0f);
}

void VuMetersRenderer::buildGeometry() {
    trackVertices_.clear();
    fillVertices_.clear();

    if (widthPx_ <= 0 || heightPx_ <= 0) return;

    float w = static_cast<float>(widthPx_);
    float h = static_cast<float>(heightPx_);

    int rows = stereo_ ? 2 : 1;
    // Meters keep presence on large screens by scaling with canvas width.
    float uiScale = std::clamp(w / (420.0f * density_), 1.0f, 1.8f);
    float rowHeightPx = 14.0f * density_ * uiScale;
    float rowGapPx = 8.0f * density_ * uiScale;
    float horizontalPadPx = 16.0f * density_ * uiScale;
    float verticalPadPx = 12.0f * density_ * uiScale;
    float labelWidthPx = 44.0f * density_ * uiScale;
    float labelGapPx = 4.0f * density_ * uiScale;

    float contentHeight = (rows * rowHeightPx) + ((rows - 1) * rowGapPx);
    float topY = (anchor_ == 1)
        ? std::max(verticalPadPx, (h - contentHeight) * 0.5f)
        : (anchor_ == 0) ? verticalPadPx : (h - verticalPadPx - contentHeight);
    float trackX = horizontalPadPx + labelWidthPx + labelGapPx;
    float trackWidth = std::max(1.0f, w - trackX - horizontalPadPx);
    float trackRadius = rowHeightPx * 0.5f;

    for (int idx = 0; idx < rows; ++idx) {
        float y = topY + (idx * (rowHeightPx + rowGapPx));
        float raw = (idx == 0) ? leftPeak_ : rightPeak_;
        float value = computeVuDbLevel(raw);

        // Track
        gl::GlPrimitives::appendRoundedRectTriangles(trackX, y, trackWidth, rowHeightPx, trackRadius, 3, trackVertices_);

        // Fill
        float fillW = trackWidth * value;
        if (fillW > 1.0f) {
            gl::GlPrimitives::appendRoundedRectTriangles(trackX, y, fillW, rowHeightPx, trackRadius, 3, fillVertices_);
        }
    }
}

void VuMetersRenderer::buildLabels() {
    textBatcher_.clear();
    if (widthPx_ <= 0 || heightPx_ <= 0) return;
    float h = static_cast<float>(heightPx_);

    int rows = stereo_ ? 2 : 1;
    float uiScale = std::clamp(static_cast<float>(widthPx_) / (420.0f * density_), 1.0f, 1.8f);
    float rowHeightPx = 14.0f * density_ * uiScale;
    float rowGapPx = 8.0f * density_ * uiScale;
    float horizontalPadPx = 16.0f * density_ * uiScale;
    float verticalPadPx = 12.0f * density_ * uiScale;

    float contentHeight = (rows * rowHeightPx) + ((rows - 1) * rowGapPx);
    float topY = (anchor_ == 1)
        ? std::max(verticalPadPx, (h - contentHeight) * 0.5f)
        : (anchor_ == 0) ? verticalPadPx : (h - verticalPadPx - contentHeight);

    float scale = (11.0f * density_ * uiScale) / fontAtlas_.getBaseFontSizePx();
    float textHeight = fontAtlas_.getLineHeightPx() * scale;

    gl::Color4f c = gl::argbToColor4f(labelColorArgb_);

    for (int idx = 0; idx < rows; ++idx) {
        float y = topY + (idx * (rowHeightPx + rowGapPx));
        float textY = y + (rowHeightPx - textHeight) * 0.5f;
        std::string label = (rows > 1) ? ((idx == 0) ? "Left" : "Right") : "Mono";
        textBatcher_.addText(fontAtlas_, label, horizontalPadPx, textY, scale, c.r, c.g, c.b, c.a, true);
    }
}

void VuMetersRenderer::drawLabels() {
    buildLabels();

    if (textBatcher_.getVertexCount() > 0) {
        textProgram_.draw(
            textBatcher_.getBufferData(),
            static_cast<int>(textBatcher_.getVertexCount()),
            fontAtlas_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
}

void VuMetersRenderer::render() {
    buildGeometry();

    if (!trackVertices_.empty()) {
        flatRenderer_.drawTriangles(
            trackVertices_.data(),
            static_cast<int>(trackVertices_.size() / 2),
            trackColorArgb_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }

    if (!fillVertices_.empty()) {
        flatRenderer_.drawTriangles(
            fillVertices_.data(),
            static_cast<int>(fillVertices_.size() / 2),
            fillColorArgb_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }

    drawLabels();
}

void VuMetersRenderer::renderVk(
    void* cmdBuffer,
    void* pipelines,
    void* dynamicVertexBuffer,
    uint64_t fontDescriptorSet,
    float width,
    float height
) {
    if (!cmdBuffer || !pipelines || !dynamicVertexBuffer || width <= 0.0f || height <= 0.0f) return;

    VkCommandBuffer cmd = static_cast<VkCommandBuffer>(cmdBuffer);
    auto* prims = static_cast<vk::VkPrimitivePipelines*>(pipelines);
    auto* dynBuffer = static_cast<vk::VkDynamicVertexBuffer*>(dynamicVertexBuffer);
    const auto& table = vk::VkLoader::table();

    VkDescriptorSet fontDesc{};
    std::memcpy(&fontDesc, &fontDescriptorSet, sizeof(VkDescriptorSet));

    buildGeometry();
    buildLabels();
    VkBuffer buffer = dynBuffer->getBuffer();

    auto withAlpha = [this](uint32_t argb) {
        const float a = ((argb >> 24) & 0xFF) / 255.0f * alpha_;
        const uint32_t ai = static_cast<uint32_t>(std::clamp(a, 0.0f, 1.0f) * 255.0f);
        return (argb & 0x00FFFFFF) | (ai << 24);
    };

    auto drawTris = [&](const std::vector<float>& verts, uint32_t argb) {
        if (verts.empty()) return;
        size_t sizeBytes = verts.size() * sizeof(float);
        size_t offset = dynBuffer->allocate(verts.data(), sizeBytes);
        if (offset == static_cast<size_t>(-1)) return;
        prims->bindFlatTriangles(cmd, width, height, withAlpha(argb));
        VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
        table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
        table.vkCmdDraw(cmd, static_cast<uint32_t>(verts.size() / 2), 1, 0, 0);
    };

    // Track, fill, then labels: same order as the GL path.
    drawTris(trackVertices_, trackColorArgb_);
    drawTris(fillVertices_, fillColorArgb_);

    if (fontDesc != VK_NULL_HANDLE && textBatcher_.getVertexCount() > 0) {
        size_t sizeBytes = textBatcher_.getVertexCount() * 8 * sizeof(float);
        size_t offset = dynBuffer->allocate(textBatcher_.getBufferData(), sizeBytes);
        if (offset != static_cast<size_t>(-1)) {
            prims->bindText(cmd, width, height, fontDesc);
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, static_cast<uint32_t>(textBatcher_.getVertexCount()), 1, 0, 0);
        }
    }
}

} // namespace silicon::vis
