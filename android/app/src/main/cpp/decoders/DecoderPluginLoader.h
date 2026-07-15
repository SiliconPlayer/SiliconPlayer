#ifndef SILICONPLAYER_DECODERPLUGINLOADER_H
#define SILICONPLAYER_DECODERPLUGINLOADER_H

#include "AudioDecoder.h"

#include <memory>
#include <string>

class DecoderPluginLoader {
public:
    static DecoderPluginLoader& getInstance();

    std::unique_ptr<AudioDecoder> createDecoder(const std::string& libraryName);

private:
    DecoderPluginLoader();
    ~DecoderPluginLoader();
    DecoderPluginLoader(const DecoderPluginLoader&) = delete;
    DecoderPluginLoader& operator=(const DecoderPluginLoader&) = delete;
};

#endif // SILICONPLAYER_DECODERPLUGINLOADER_H
