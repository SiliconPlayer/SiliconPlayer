#include "Vio2sfDecoder.h"

extern "C" __attribute__((visibility("default")))
AudioDecoder* siliconplayer_create_decoder() {
    return new Vio2sfDecoder();
}
