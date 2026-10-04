#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

SHADERS=(
    "flat.vert:kFlatVertSpv"
    "flat.frag:kFlatFragSpv"
    "wave_line.vert:kWaveLineVertSpv"
    "wave_line.frag:kWaveLineFragSpv"
    "text.vert:kTextVertSpv"
    "text.frag:kTextFragSpv"
)

OUT_FILE="${SCRIPT_DIR}/vk_spv_shaders.h"

for item in "${SHADERS[@]}"; do
    SRC="${item%%:*}"
    glslc --target-env=vulkan1.1 -O "${SCRIPT_DIR}/${SRC}" -o "${TEMP_DIR}/${SRC}.spv"
    spirv-val "${TEMP_DIR}/${SRC}.spv"
done

python3 - << EOF
import os

shaders = [
    ("flat.vert.spv", "kFlatVertSpv"),
    ("flat.frag.spv", "kFlatFragSpv"),
    ("wave_line.vert.spv", "kWaveLineVertSpv"),
    ("wave_line.frag.spv", "kWaveLineFragSpv"),
    ("text.vert.spv", "kTextVertSpv"),
    ("text.frag.spv", "kTextFragSpv"),
]

out_path = "${OUT_FILE}"
temp_dir = "${TEMP_DIR}"

with open(out_path, "w") as out:
    out.write("#pragma once\n\n#include <cstdint>\n#include <cstddef>\n\nnamespace silicon::vis::vk::shaders {\n\n")
    for fname, varname in shaders:
        path = os.path.join(temp_dir, fname)
        with open(path, "rb") as f:
            data = f.read()
        assert len(data) % 4 == 0, f"{fname} size not aligned to 4 bytes"
        words = [int.from_bytes(data[i:i+4], byteorder='little') for i in range(0, len(data), 4)]
        out.write(f"// {fname} ({len(data)} bytes, {len(words)} words)\n")
        out.write(f"alignas(4) const uint32_t {varname}[] = {{\n")
        for i in range(0, len(words), 8):
            chunk = words[i:i+8]
            out.write("    " + ", ".join(f"0x{w:08x}u" for w in chunk) + ",\n")
        out.write("};\n")
        out.write(f"const size_t {varname}Size = sizeof({varname});\n\n")
    out.write("} // namespace silicon::vis::vk::shaders\n")
EOF

echo "Compiled SPIR-V shaders successfully into ${OUT_FILE}"
