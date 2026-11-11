#ifndef SILICONPLAYER_SPATIAL_SPATIALIZER_CONFIG_JSON_H
#define SILICONPLAYER_SPATIAL_SPATIALIZER_CONFIG_JSON_H

#include "ISpatializer.h"

#include <string>
#include <string_view>

namespace siliconplayer::spatial {

SpatializerConfig parseSpatializerConfigJson(std::string_view json);
std::string serializeSpatializerConfigJson(const SpatializerConfig& config);

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_SPATIALIZER_CONFIG_JSON_H
