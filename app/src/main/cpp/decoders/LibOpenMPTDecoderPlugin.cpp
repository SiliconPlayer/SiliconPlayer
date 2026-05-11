#include "LibOpenMPTDecoder.h"

extern "C" __attribute__((visibility("default")))
AudioDecoder* siliconplayer_create_decoder() {
    return new LibOpenMPTDecoder();
}
