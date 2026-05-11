#include "AudioEngine.h"

#include "decoders/DecoderRegistry.h"
#include "decoders/DecoderPluginLoader.h"
#include "decoders/UadeExtensions.h"

#include <cstring>
#include <utility>

namespace {
    const std::vector<std::string>& getStaticFfmpegExtensions() {
        static const std::vector<std::string> extensions = {
                "3g2", "3gp", "aa", "aac", "ac3", "aif", "aifc", "aiff", "alac",
                "amr", "ape", "asf", "au", "caf", "dts", "dsf", "eac3", "flac",
                "m4a", "m4b", "m4p", "m4r", "mka", "mkv", "mov", "mp2", "mp3",
                "mp4", "mpc", "oga", "ogg", "opus", "qcp", "ra", "tta", "voc",
                "w64", "wav", "weba", "webm", "wma", "wmv", "wv", "xwma"
        };
        return extensions;
    }

    const std::vector<std::string>& getStaticOpenMptExtensions() {
        static const std::vector<std::string> extensions = {
                "669", "amf", "ams", "c67", "dbm", "digi", "dmf", "dsm", "dtm",
                "far", "gdm", "ice", "imf", "it", "j2b", "m15", "mdl", "med",
                "mms", "mod", "mt2", "mtm", "nst", "okt", "plm", "psm", "pt36",
                "s3m", "sfx", "sfx2", "st26", "stk", "stm", "stp", "symmod",
                "ult", "umx", "wow", "xm"
        };
        return extensions;
    }

    struct DecoderRegistration {
        DecoderRegistration() {
            DecoderStaticInfo ffmpegStaticInfo;
            ffmpegStaticInfo.hasPlaybackCapabilities = true;
            ffmpegStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
                    AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE |
                    AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK |
                    AudioDecoder::PLAYBACK_CAP_ASYNC_DIRECT_SEEK;
            ffmpegStaticInfo.hasRepeatModeCapabilities = true;
            ffmpegStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            ffmpegStaticInfo.hasTimelineMode = true;
            ffmpegStaticInfo.timelineMode = AudioDecoder::TimelineMode::ContinuousLinear;
            ffmpegStaticInfo.hasFixedSampleRateHz = true;
            ffmpegStaticInfo.fixedSampleRateHz = 0;
            ffmpegStaticInfo.optionApplyPolicy = [](const char*) {
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("FFmpeg", getStaticFfmpegExtensions(), []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_ffmpeg_decoder.so");
            }, 0, std::move(ffmpegStaticInfo));

            DecoderStaticInfo openMptStaticInfo;
            openMptStaticInfo.hasPlaybackCapabilities = true;
            openMptStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
                    AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE |
                    AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK;
            openMptStaticInfo.hasRepeatModeCapabilities = true;
            openMptStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            openMptStaticInfo.hasTimelineMode = true;
            openMptStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            openMptStaticInfo.hasFixedSampleRateHz = true;
            openMptStaticInfo.fixedSampleRateHz = 0;
            openMptStaticInfo.optionApplyPolicy = [](const char*) {
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("LibOpenMPT", getStaticOpenMptExtensions(), []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_openmpt_decoder.so");
            }, 10, std::move(openMptStaticInfo));

            DecoderStaticInfo vgmStaticInfo;
            vgmStaticInfo.hasPlaybackCapabilities = true;
            vgmStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
                    AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK;
            vgmStaticInfo.hasRepeatModeCapabilities = true;
            vgmStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            vgmStaticInfo.hasTimelineMode = true;
            vgmStaticInfo.timelineMode = AudioDecoder::TimelineMode::ContinuousLinear;
            vgmStaticInfo.hasFixedSampleRateHz = true;
            vgmStaticInfo.fixedSampleRateHz = 0;
            vgmStaticInfo.optionApplyPolicy = [](const char* name) {
                if (name == nullptr) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                const bool restart =
                        std::strcmp(name, "vgmplay.resample_mode") == 0 ||
                        std::strcmp(name, "vgmplay.chip_sample_mode") == 0 ||
                        std::strcmp(name, "vgmplay.chip_sample_rate_hz") == 0 ||
                        std::strncmp(name, "vgmplay.chip_core.", std::strlen("vgmplay.chip_core.")) == 0;
                return restart
                        ? AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART
                        : AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("VGMPlay", {"vgm", "vgz", "vgm.gz"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_vgm_decoder.so");
            }, 5, std::move(vgmStaticInfo));

            DecoderStaticInfo gmeStaticInfo;
            gmeStaticInfo.hasPlaybackCapabilities = true;
            gmeStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            gmeStaticInfo.hasRepeatModeCapabilities = true;
            gmeStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            gmeStaticInfo.hasTimelineMode = true;
            gmeStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            gmeStaticInfo.hasFixedSampleRateHz = true;
            gmeStaticInfo.fixedSampleRateHz = 0;
            gmeStaticInfo.optionApplyPolicy = [](const char* name) {
                if (name == nullptr) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                if (std::strcmp(name, "gme.spc_use_builtin_fade") == 0 ||
                    std::strcmp(name, "gme.spc_use_native_sample_rate") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("Game Music Emu", {
                    "ay", "gbs", "gym", "hes", "kss", "nsf", "nsfe", "sap", "spc", "vgm", "vgz"
            }, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_gme_decoder.so");
            }, 6, std::move(gmeStaticInfo));

            DecoderStaticInfo crsidStaticInfo;
            crsidStaticInfo.hasPlaybackCapabilities = true;
            crsidStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE;
            crsidStaticInfo.hasRepeatModeCapabilities = true;
            crsidStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            crsidStaticInfo.hasTimelineMode = true;
            crsidStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            crsidStaticInfo.hasFixedSampleRateHz = true;
            crsidStaticInfo.fixedSampleRateHz = 0;
            crsidStaticInfo.optionApplyPolicy = [](const char* name) {
                if (name == nullptr) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                if (std::strcmp(name, "crsid.clock_mode") == 0 ||
                    std::strcmp(name, "crsid.sid_model_mode") == 0 ||
                    std::strcmp(name, "crsid.quality_mode") == 0 ||
                    std::strcmp(name, "crsid.filter_6581_preset") == 0 ||
                    std::strcmp(name, "crsid.stereo") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("cRSID", {"sid", "psid", "rsid"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_crsid_decoder.so");
            }, 4, std::move(crsidStaticInfo));

            DecoderStaticInfo sidplayfpStaticInfo;
            sidplayfpStaticInfo.hasPlaybackCapabilities = true;
            sidplayfpStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            sidplayfpStaticInfo.hasRepeatModeCapabilities = true;
            sidplayfpStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            sidplayfpStaticInfo.hasTimelineMode = true;
            sidplayfpStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            sidplayfpStaticInfo.hasFixedSampleRateHz = true;
            sidplayfpStaticInfo.fixedSampleRateHz = 0;
            sidplayfpStaticInfo.optionApplyPolicy = [](const char* name) {
                if (name == nullptr) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                if (std::strcmp(name, "sidplayfp.backend") == 0 ||
                    std::strcmp(name, "sidplayfp.clock_mode") == 0 ||
                    std::strcmp(name, "sidplayfp.sid_model_mode") == 0 ||
                    std::strcmp(name, "sidplayfp.force_sid_model") == 0 ||
                    std::strcmp(name, "sidplayfp.sid_model") == 0 ||
                    std::strcmp(name, "sidplayfp.digiboost_8580") == 0 ||
                    std::strcmp(name, "sidplayfp.residfp_fast_sampling") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("LibSIDPlayFP", {
                    "sid", "psid", "rsid", "mus", "str", "prg", "p00", "c64", "dat"
            }, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_libsidplayfp_decoder.so");
            }, 7, std::move(sidplayfpStaticInfo));

            DecoderStaticInfo lazyUsf2StaticInfo;
            lazyUsf2StaticInfo.hasPlaybackCapabilities = true;
            lazyUsf2StaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
                    AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE;
            lazyUsf2StaticInfo.hasRepeatModeCapabilities = true;
            lazyUsf2StaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            lazyUsf2StaticInfo.hasTimelineMode = true;
            lazyUsf2StaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            lazyUsf2StaticInfo.hasFixedSampleRateHz = true;
            lazyUsf2StaticInfo.fixedSampleRateHz = 0;
            lazyUsf2StaticInfo.optionApplyPolicy = [](const char* name) {
                if (name != nullptr && std::strcmp(name, "lazyusf2.use_hle_audio") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("LazyUSF2", {"usf", "miniusf"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_lazyusf2_decoder.so");
            }, 8, std::move(lazyUsf2StaticInfo));

            DecoderStaticInfo vio2sfStaticInfo;
            vio2sfStaticInfo.hasPlaybackCapabilities = true;
            vio2sfStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_FIXED_SAMPLE_RATE;
            vio2sfStaticInfo.hasRepeatModeCapabilities = true;
            vio2sfStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            vio2sfStaticInfo.hasTimelineMode = true;
            vio2sfStaticInfo.timelineMode = AudioDecoder::TimelineMode::ContinuousLinear;
            vio2sfStaticInfo.hasFixedSampleRateHz = true;
            vio2sfStaticInfo.fixedSampleRateHz = 44100;
            vio2sfStaticInfo.optionApplyPolicy = [](const char*) {
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("Vio2SF", {"2sf", "mini2sf"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_vio2sf_decoder.so");
            }, 9, std::move(vio2sfStaticInfo));

            DecoderStaticInfo sc68StaticInfo;
            sc68StaticInfo.hasPlaybackCapabilities = true;
            sc68StaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            sc68StaticInfo.hasRepeatModeCapabilities = true;
            sc68StaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            sc68StaticInfo.hasTimelineMode = true;
            sc68StaticInfo.timelineMode = AudioDecoder::TimelineMode::ContinuousLinear;
            sc68StaticInfo.hasFixedSampleRateHz = true;
            sc68StaticInfo.fixedSampleRateHz = 0;
            sc68StaticInfo.optionApplyPolicy = [](const char* name) {
                if (name == nullptr) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                if (std::strcmp(name, "sc68.asid") == 0 ||
                    std::strcmp(name, "sc68.default_time_seconds") == 0 ||
                    std::strcmp(name, "sc68.ym_engine") == 0 ||
                    std::strcmp(name, "sc68.ym_volmodel") == 0 ||
                    std::strcmp(name, "sc68.amiga_filter") == 0 ||
                    std::strcmp(name, "sc68.amiga_blend") == 0 ||
                    std::strcmp(name, "sc68.amiga_clock") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("SC68", {"sc68", "sndh"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_sc68_decoder.so");
            }, 11, std::move(sc68StaticInfo));

            DecoderStaticInfo adplugStaticInfo;
            adplugStaticInfo.hasPlaybackCapabilities = true;
            adplugStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            adplugStaticInfo.hasRepeatModeCapabilities = true;
            adplugStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            adplugStaticInfo.hasTimelineMode = true;
            adplugStaticInfo.timelineMode = AudioDecoder::TimelineMode::ContinuousLinear;
            adplugStaticInfo.hasFixedSampleRateHz = true;
            adplugStaticInfo.fixedSampleRateHz = 0;
            adplugStaticInfo.optionApplyPolicy = [](const char* name) {
                if (name != nullptr && std::strcmp(name, "adplug.opl_engine") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("AdPlug", {
                    "hsc", "sng", "imf", "wlf", "adlib", "a2m", "a2t", "xms",
                    "bam", "cmf", "adl", "d00", "dfm", "hsp", "ksm", "mad",
                    "mus", "mdy", "ims", "mdi", "mid", "sci", "laa", "mkj",
                    "cff", "dmo", "s3m", "dtm", "mtk", "mtr", "rad", "rac",
                    "raw", "sat", "sa2", "xad", "lds", "plx", "m", "rol",
                    "xsm", "dro", "pis", "msc", "rix", "mkf", "jbm", "got",
                    "vgm", "vgz", "sop", "hsq", "sqx", "sdb", "agd", "ha2"
            }, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_adplug_decoder.so");
            }, 12, std::move(adplugStaticInfo));

            DecoderStaticInfo uadeStaticInfo;
            uadeStaticInfo.hasPlaybackCapabilities = true;
            uadeStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
                    AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE;
            uadeStaticInfo.hasRepeatModeCapabilities = true;
            uadeStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            uadeStaticInfo.hasTimelineMode = true;
            uadeStaticInfo.timelineMode = AudioDecoder::TimelineMode::ContinuousLinear;
            uadeStaticInfo.hasFixedSampleRateHz = true;
            uadeStaticInfo.fixedSampleRateHz = 0;
            uadeStaticInfo.optionApplyPolicy = [](const char* name) {
                if (name == nullptr) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                if (std::strcmp(name, "uade.filter_enabled") == 0 ||
                    std::strcmp(name, "uade.ntsc_mode") == 0 ||
                    std::strcmp(name, "uade.panning_mode") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("UADE", getUadeSupportedExtensions(), []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_uade_decoder.so");
            }, 14, std::move(uadeStaticInfo));

            DecoderStaticInfo hivelyStaticInfo;
            hivelyStaticInfo.hasPlaybackCapabilities = true;
            hivelyStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            hivelyStaticInfo.hasRepeatModeCapabilities = true;
            hivelyStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            hivelyStaticInfo.hasTimelineMode = true;
            hivelyStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            hivelyStaticInfo.hasFixedSampleRateHz = true;
            hivelyStaticInfo.fixedSampleRateHz = 0;
            hivelyStaticInfo.optionApplyPolicy = [](const char*) {
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("HivelyTracker", {"ahx", "hvl"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_hivelytracker_decoder.so");
            }, 13, std::move(hivelyStaticInfo));

            DecoderStaticInfo klystrackStaticInfo;
            klystrackStaticInfo.hasPlaybackCapabilities = true;
            klystrackStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            klystrackStaticInfo.hasRepeatModeCapabilities = true;
            klystrackStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            klystrackStaticInfo.hasTimelineMode = true;
            klystrackStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            klystrackStaticInfo.hasFixedSampleRateHz = true;
            klystrackStaticInfo.fixedSampleRateHz = 0;
            klystrackStaticInfo.optionApplyPolicy = [](const char*) {
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("Klystrack-plus", {"kt"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_klystrack_decoder.so");
            }, 15, std::move(klystrackStaticInfo));

            DecoderStaticInfo furnaceStaticInfo;
            furnaceStaticInfo.hasPlaybackCapabilities = true;
            furnaceStaticInfo.playbackCapabilities =
                    AudioDecoder::PLAYBACK_CAP_SEEK |
                    AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE |
                    AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK |
                    AudioDecoder::PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
            furnaceStaticInfo.hasRepeatModeCapabilities = true;
            furnaceStaticInfo.repeatModeCapabilities =
                    AudioDecoder::REPEAT_CAP_TRACK |
                    AudioDecoder::REPEAT_CAP_LOOP_POINT;
            furnaceStaticInfo.hasTimelineMode = true;
            furnaceStaticInfo.timelineMode = AudioDecoder::TimelineMode::Discontinuous;
            furnaceStaticInfo.hasFixedSampleRateHz = true;
            furnaceStaticInfo.fixedSampleRateHz = 0;
            furnaceStaticInfo.optionApplyPolicy = [](const char* name) {
                if (!name) {
                    return AudioDecoder::OPTION_APPLY_LIVE;
                }
                if (std::strcmp(name, "furnace.ym2612_core") == 0 ||
                    std::strcmp(name, "furnace.sn_core") == 0 ||
                    std::strcmp(name, "furnace.nes_core") == 0 ||
                    std::strcmp(name, "furnace.c64_core") == 0 ||
                    std::strcmp(name, "furnace.gb_quality") == 0 ||
                    std::strcmp(name, "furnace.dsid_quality") == 0 ||
                    std::strcmp(name, "furnace.ay_core") == 0) {
                    return AudioDecoder::OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
                }
                return AudioDecoder::OPTION_APPLY_LIVE;
            };
            DecoderRegistry::getInstance().registerDecoder("Furnace", {"fur", "dmf"}, []() {
                return DecoderPluginLoader::getInstance().createDecoder("libsiliconplayer_furnace_decoder.so");
            }, 16, std::move(furnaceStaticInfo));
        }
    };

    static DecoderRegistration registration;
}

AudioEngine::AudioEngine() {
    updateRenderQueueTuning();
    seekWorkerThread = std::thread([this]() { seekWorkerLoop(); });
    renderWorkerThread = std::thread([this]() { renderWorkerLoop(); });
    createStream();
}

AudioEngine::~AudioEngine() {
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekWorkerStop = true;
        seekRequestPending = false;
        seekAbortRequested.store(true);
    }
    seekWorkerCv.notify_all();
    if (seekWorkerThread.joinable()) {
        seekWorkerThread.join();
    }
    {
        std::lock_guard<std::mutex> lock(renderQueueMutex);
        renderWorkerStop = true;
    }
    renderWorkerCv.notify_all();
    if (renderWorkerThread.joinable()) {
        renderWorkerThread.join();
    }
    std::lock_guard<std::mutex> lock(decoderMutex);
    freeOutputSoxrContextLocked();
    closeStream();
}
