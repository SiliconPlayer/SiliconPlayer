package com.flopster101.siliconplayer

internal enum class AboutEntityKind {
    Core,
    Library
}

internal data class AboutEntityLink(
    val label: String,
    val url: String
)

internal data class AboutEntity(
    val id: String,
    val kind: AboutEntityKind,
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val links: List<AboutEntityLink> = emptyList(),
    val integrationNotes: List<String> = emptyList()
)

internal object AboutCatalog {
    private val coreEntries: List<AboutEntity> = listOf(
        AboutEntity(
            id = "core.ffmpeg",
            kind = AboutEntityKind.Core,
            name = DecoderNames.FFMPEG,
            description = "General-purpose decoding backend used for mainstream audio containers and codecs.",
            author = "FFmpeg Project contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://ffmpeg.org/"),
                AboutEntityLink("Source", "https://git.ffmpeg.org/ffmpeg.git")
            )
        ),
        AboutEntity(
            id = "core.libopenmpt",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIB_OPEN_MPT,
            description = "Tracker module playback library for MOD/XM/S3M/IT and related formats.",
            author = "OpenMPT Project developers and contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Project", "https://lib.openmpt.org/"),
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/openmpt.git")
            )
        ),
        AboutEntity(
            id = "core.vgmplay",
            kind = AboutEntityKind.Core,
            name = DecoderNames.VGM_PLAY,
            description = "Chip-focused VGM playback stack based on libvgm.",
            author = "Valley Bell and libvgm contributors",
            license = "Mixed per-chip licenses (BSD/LGPL/GPL and others; see upstream sources)",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/libvgm")
            )
        ),
        AboutEntity(
            id = "core.gme",
            kind = AboutEntityKind.Core,
            name = DecoderNames.GAME_MUSIC_EMU,
            description = "Multi-system game music emulator for formats like NSF, SPC, GBS, HES, and others.",
            author = "Shay Green, libgme maintainers, and contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/game-music-emu")
            )
        ),
        AboutEntity(
            id = "core.crsid",
            kind = AboutEntityKind.Core,
            name = DecoderNames.C_RSID,
            description = "Integer-focused Commodore 64 SID playback core based on the cRSID engine.",
            author = "Hermit (Mihaly Horvath)",
            license = "Upstream custom permissive notice",
            links = listOf(
                AboutEntityLink("Project", "https://csdb.dk/release/?id=261057")
            )
        ),
        AboutEntity(
            id = "core.libsidplayfp",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIB_SID_PLAY_FP,
            description = "Cycle-based Commodore 64 SID playback library with high quality SID emulation backends.",
            author = "Simon White, Antti Lankila, Leandro Nini, and contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://libsidplayfp.github.io/libsidplayfp/"),
                AboutEntityLink("Source", "https://github.com/libsidplayfp/libsidplayfp")
            )
        ),
        AboutEntity(
            id = "core.lazyusf2",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LAZY_USF2,
            description = "Nintendo 64 USF playback core derived from Mupen64plus-era audio emulation code.",
            author = "lazyusf2 and Mupen64plus contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/lazyusf2")
            )
        ),
        AboutEntity(
            id = "core.vio2sf",
            kind = AboutEntityKind.Core,
            name = DecoderNames.VIO2_SF,
            description = "Nintendo DS 2SF playback core built around a DeSmuME-based audio state renderer.",
            author = "Christopher Snowhill and DeSmuME contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://bitbucket.org/kode54/vio2sf")
            )
        ),
        AboutEntity(
            id = "core.libupse",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIB_UPSE,
            description = "PlayStation PSF and PSF2 playback core.",
            author = "William Pitcock and UPSE contributors",
            license = "GPL-2.0-only",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/libupse")
            )
        ),
        AboutEntity(
            id = "core.viogsf",
            kind = AboutEntityKind.Core,
            name = DecoderNames.VIOGSF,
            description = "Game Boy Advance GSF playback core.",
            author = "kode54 and VBA contributors",
            license = "GPL-2.0-only",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/viogsf")
            )
        ),
        AboutEntity(
            id = "core.sc68",
            kind = AboutEntityKind.Core,
            name = DecoderNames.SC68,
            description = "Atari ST/Amiga music playback core for SC68 and SNDH tracks.",
            author = "Benjamin Gerard and sc68 contributors",
            license = "GPL-3.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://sourceforge.net/p/sc68/"),
                AboutEntityLink("Source", "https://sourceforge.net/p/sc68/code/HEAD/tree/")
            )
        ),
        AboutEntity(
            id = "core.adplug",
            kind = AboutEntityKind.Core,
            name = DecoderNames.AD_PLUG,
            description = "OPL2/OPL3 replayer core for many DOS-era AdLib and OPL music formats.",
            author = "Simon Peter and AdPlug contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://adplug.github.io/"),
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/adplug")
            )
        ),
        AboutEntity(
            id = "core.uade",
            kind = AboutEntityKind.Core,
            name = DecoderNames.UADE,
            description = "Amiga music playback core using Unix Amiga Delitracker Emulator format handlers.",
            author = "UADE contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://zakalwe.fi/uade/"),
                AboutEntityLink("Source", "https://gitlab.com/mvtiaine/uade")
            )
        ),
        AboutEntity(
            id = "core.hivelytracker",
            kind = AboutEntityKind.Core,
            name = DecoderNames.HIVELY_TRACKER,
            description = "AHX/HVL tracker replayer core for Amiga-style chiptune modules.",
            author = "Xeron, Xigh, and HivelyTracker contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/hivelytracker")
            )
        ),
        AboutEntity(
            id = "core.klystrack",
            kind = AboutEntityKind.Core,
            name = DecoderNames.KLYSTRACK,
            description = "klystrack-plus module replay core using the klystron audio engine.",
            author = "Georgy Saraykin (LTVA1) and Klystrack-plus contributors",
            license = "MIT License",
            links = listOf(
                AboutEntityLink("Project", "https://github.com/LTVA1/klystrack"),
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/klystrack")
            )
        ),
        AboutEntity(
            id = "core.furnace",
            kind = AboutEntityKind.Core,
            name = DecoderNames.FURNACE,
            description = "Furnace Tracker playback core for .fur/.dmf modules plus imported tracker formats (.ftm, .fc, .tfm, .mod, .xm, .s3m, .it) using the upstream headless engine.",
            author = "tildearrow and Furnace contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://tildearrow.org/furnace/"),
                AboutEntityLink("Source", "https://github.com/tildearrow/furnace")
            )
        ),
        AboutEntity(
            id = "core.dnfamitracker",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIB_DN_FAMITRACKER,
            description = "NES/Famicom tracker playback for .dnm and .ftm modules, including expansion-chip audio. Fork of Dn-FamiTracker decoupled as a headless library, with adaptations for SiliconPlayer.",
            author = "D.P.C.M., Jonathan Liss, Flopster101 and contributors",
            license = "GPL-3.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://github.com/Dn-Programming-Core-Management/Dn-FamiTracker"),
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/Dn-FamiTracker")
            ),
            integrationNotes = listOf(
                "Fork of Dn-FamiTracker decoupled from the upstream MFC app as a headless playback library, with adaptations for SiliconPlayer."
            )
        ),
        AboutEntity(
            id = "core.ayfly",
            kind = AboutEntityKind.Core,
            name = DecoderNames.AYFLY,
            description = "ZX Spectrum and AY-8910 music player library.",
            author = "Deryabin Andrew and contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/ayfly")
            )
        ),
        AboutEntity(
            id = "core.libxmp",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIBXMP,
            description = "Module player engine covering MOD, XM, S3M, IT and legacy tracker formats.",
            author = "Claudio Matsuoka, Hipolito Carraro Jr. and contributors",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/libxmp")
            )
        ),
        AboutEntity(
            id = "core.ufmod",
            kind = AboutEntityKind.Core,
            name = DecoderNames.UFMOD,
            description = "uFMOD rewritten in C for portable XM playback.",
            author = "Flopster101, Asterix and Quantum",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Project", "https://ufmod.sourceforge.io/"),
                AboutEntityLink("Source", "https://github.com/Flopster101/uFMOD-C")
            )
        )
    )

    private val libraryEntries: List<AboutEntity> = listOf(
        AboutEntity(
            id = "lib.openmpt_dsp_effects",
            kind = AboutEntityKind.Library,
            name = "OpenMPT DSP effects",
            description = "Audio DSP effect implementations adapted from OpenMPT sounddsp components (Bass Expansion, Reverb, Surround, BitCrush).",
            author = "OpenMPT Project developers and contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Project", "https://openmpt.org/"),
                AboutEntityLink("Source", "https://github.com/OpenMPT/openmpt.git")
            ),
            integrationNotes = listOf(
                "Integrated under app/src/main/cpp/effects/openmpt_dsp/ with local attribution and license copy.",
                "Silicon Player uses a native port of OpenMPT DSP routines in the app audio processing pipeline."
            )
        ),
        AboutEntity(
            id = "lib.psflib",
            kind = AboutEntityKind.Library,
            name = "PSFLib",
            description = "PSF/2SF container parsing and library dependency loading helper.",
            author = "Christopher Snowhill",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/kode54/psflib")
            ),
            integrationNotes = listOf(
                "Used by Vio2SF to resolve mini2SF references and parse metadata tags."
            )
        ),
        AboutEntity(
            id = "lib.mbedtls",
            kind = AboutEntityKind.Library,
            name = "mbedTLS",
            description = "Lightweight cryptographic and SSL/TLS library.",
            author = "Arm Limited and contributors",
            license = "Apache-2.0",
            links = listOf(
                AboutEntityLink("Project", "https://www.trustedfirmware.org/projects/mbed-tls/"),
                AboutEntityLink("Source", "https://github.com/Mbed-TLS/mbedtls")
            )
        ),
        AboutEntity(
            id = "lib.libsoxr",
            kind = AboutEntityKind.Library,
            name = "libsoxr",
            description = "High-quality sample-rate conversion library.",
            author = "Rob Sykes and contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://git.code.sf.net/p/soxr/code")
            ),
            integrationNotes = listOf(
                "Available as an external resampling backend in the native pipeline."
            )
        ),
        AboutEntity(
            id = "lib.libresidfp",
            kind = AboutEntityKind.Library,
            name = "libresidfp",
            description = "Floating-point SID chip emulation backend used by libsidplayfp.",
            author = "libsidplayfp/libresidfp contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libsidplayfp/libresidfp")
            ),
            integrationNotes = listOf(
                "Provides higher fidelity SID emulation paths for the SID core settings."
            )
        ),
        AboutEntity(
            id = "lib.resid",
            kind = AboutEntityKind.Library,
            name = "reSID",
            description = "Classic SID emulation backend used alongside reSIDfp in SID playback paths.",
            author = "Dag Lem and contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libsidplayfp/resid")
            ),
            integrationNotes = listOf(
                "Bundled as part of SID backend support for libsidplayfp integration."
            )
        ),
        AboutEntity(
            id = "lib.libbinio",
            kind = AboutEntityKind.Library,
            name = "libbinio",
            description = "Binary I/O support library used by AdPlug loaders.",
            author = "Simon Peter and libbinio contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://adplug.github.io/libbinio/"),
                AboutEntityLink("Source", "https://github.com/adplug/libbinio")
            ),
            integrationNotes = listOf(
                "Linked as a static dependency for the AdPlug core."
            )
        ),
        AboutEntity(
            id = "lib.miniaudio",
            kind = AboutEntityKind.Library,
            name = "miniaudio",
            description = "Single-file audio playback and capture library used for cross-platform audio backend output.",
            author = "David Reid and miniaudio contributors",
            license = "Public Domain (Unlicense) / MIT-0",
            links = listOf(
                AboutEntityLink("Project", "https://miniaud.io/"),
                AboutEntityLink("Source", "https://github.com/SiliconPlayer/miniaudio.git")
            ),
            integrationNotes = listOf(
                "Provides unified audio output backend handling across AAudio, OpenSL ES, and desktop audio pipelines."
            )
        ),
        AboutEntity(
            id = "lib.roboto",
            kind = AboutEntityKind.Library,
            name = "Roboto",
            description = "Typeface used for the VU meter labels and readouts.",
            author = "Christian Robertson and contributors",
            license = "Apache-2.0",
            links = listOf(
                AboutEntityLink("Project", "https://fonts.google.com/specimen/Roboto"),
                AboutEntityLink("Source", "https://github.com/google/roboto")
            ),
            integrationNotes = listOf(
                "Only the Medium weight ships, shared by the Android and desktop VU meters."
            )
        ),
        AboutEntity(
            id = "lib.smbj",
            kind = AboutEntityKind.Library,
            name = "SMBJ",
            description = "SMB2/3 client for network share browsing and streaming.",
            author = "Hierynomus and contributors",
            license = "Apache-2.0",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/hierynomus/smbj")
            ),
            integrationNotes = listOf(
                "Used by both file browsers for SMB share access."
            )
        ),
        AboutEntity(
            id = "lib.smbj_rpc",
            kind = AboutEntityKind.Library,
            name = "SMBJ-RPC",
            description = "DCE-RPC over SMB2 backing SMBJ share connections.",
            author = "Rapid7 and contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/rapid7/smbj-rpc")
            )
        ),
        AboutEntity(
            id = "lib.json",
            kind = AboutEntityKind.Library,
            name = "JSON-Java",
            description = "JSON parser bundled with the desktop build.",
            author = "JSON.org contributors",
            license = "JSON License",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/stleary/JSON-java")
            ),
            integrationNotes = listOf(
                "Bundled on desktop; Android resolves org.json from the framework."
            )
        ),
        AboutEntity(
            id = "lib.projectm",
            kind = AboutEntityKind.Library,
            name = "projectM",
            description = "MilkDrop-compatible music visualizer SDK.",
            author = "projectM Team",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/projectM-visualizer/projectm")
            ),
            integrationNotes = listOf(
                "Renders the projectM visualization backend on both platforms."
            )
        ),
        AboutEntity(
            id = "lib.fftw",
            kind = AboutEntityKind.Library,
            name = "FFTW",
            description = "Fast Fourier transform library used by Furnace.",
            author = "Matteo Frigo and Steven G. Johnson",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://www.fftw.org/")
            ),
            integrationNotes = listOf(
                "Shipped as built by the furnace tree."
            )
        ),
        AboutEntity(
            id = "lib.fmt",
            kind = AboutEntityKind.Library,
            name = "fmt",
            description = "Typesafe formatting library used by Furnace.",
            author = "Victor Zverovich and contributors",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/fmtlib/fmt")
            ),
            integrationNotes = listOf(
                "Shipped as built by the furnace tree."
            )
        ),
        AboutEntity(
            id = "lib.libsndfile",
            kind = AboutEntityKind.Library,
            name = "libsndfile",
            description = "Audio file IO library used by Furnace.",
            author = "Erik de Castro Lopo and contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libsndfile/libsndfile")
            ),
            integrationNotes = listOf(
                "Shipped as built by the furnace tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_nsfplay",
            kind = AboutEntityKind.Library,
            name = "NSFPlay",
            description = "NES APU and expansion-chip emulation used by Dn-FamiTracker.",
            author = "Brad Smith and contributors",
            license = "Informal permissive notice (see readme.txt)",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/bbbradsmith/nsfplay")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_emu2413_emu2149",
            kind = AboutEntityKind.Library,
            name = "emu2413 / emu2149",
            description = "VRC7 and Sunsoft 5B sound emulation used by Dn-FamiTracker.",
            author = "Mitsutaka Okazaki",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/digital-sound-antiques/emu2413"),
                AboutEntityLink("Source", "https://github.com/digital-sound-antiques/emu2149")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_mesen",
            kind = AboutEntityKind.Library,
            name = "Mesen (FDS/N163 emulation)",
            description = "FDS and N163 sound emulation used by Dn-FamiTracker.",
            author = "Sour",
            license = "GPL-3.0",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/SourMesen/Mesen2")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_blip_buffer",
            kind = AboutEntityKind.Library,
            name = "Blip_Buffer",
            description = "Band-limited audio synthesis helper used by Dn-FamiTracker.",
            author = "Shay Green",
            license = "LGPL-2.1",
            links = listOf(
                AboutEntityLink("Source", "https://www.slack.net/~ant/libs/audio.html#Blip_Buffer")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_nayuki_fft",
            kind = AboutEntityKind.Library,
            name = "Nayuki Small FFT",
            description = "Free FFT routines used by Dn-FamiTracker.",
            author = "Project Nayuki",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://www.nayuki.io/page/free-small-fft-in-multiple-languages")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_json",
            kind = AboutEntityKind.Library,
            name = "JSON for Modern C++",
            description = "JSON helper used by Dn-FamiTracker.",
            author = "Niels Lohmann",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/nlohmann/json")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        ),
        AboutEntity(
            id = "lib.dnfamitracker_libsamplerate",
            kind = AboutEntityKind.Library,
            name = "libsamplerate",
            description = "Sample-rate conversion used by Dn-FamiTracker.",
            author = "Erik de Castro Lopo",
            license = "BSD-2-Clause",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libsndfile/libsamplerate")
            ),
            integrationNotes = listOf(
                "Shipped as built by the Dn-FamiTracker tree."
            )
        )
    )

    private val pluginNameToCoreId: Map<String, String> = mapOf(
        DecoderNames.FFMPEG to "core.ffmpeg",
        DecoderNames.LIB_OPEN_MPT to "core.libopenmpt",
        DecoderNames.VGM_PLAY to "core.vgmplay",
        DecoderNames.GAME_MUSIC_EMU to "core.gme",
        DecoderNames.C_RSID to "core.crsid",
        DecoderNames.LIB_SID_PLAY_FP to "core.libsidplayfp",
        DecoderNames.LAZY_USF2 to "core.lazyusf2",
        DecoderNames.VIO2_SF to "core.vio2sf",
        DecoderNames.LIB_UPSE to "core.libupse",
        DecoderNames.VIOGSF to "core.viogsf",
        DecoderNames.SC68 to "core.sc68",
        DecoderNames.AD_PLUG to "core.adplug",
        DecoderNames.UADE to "core.uade",
        DecoderNames.HIVELY_TRACKER to "core.hivelytracker",
        DecoderNames.KLYSTRACK to "core.klystrack",
        DecoderNames.FURNACE to "core.furnace",
        DecoderNames.LIB_DN_FAMITRACKER to "core.dnfamitracker",
        DecoderNames.AYFLY to "core.ayfly",
        DecoderNames.LIBXMP to "core.libxmp",
        DecoderNames.UFMOD to "core.ufmod"
    )

    private val entityById: Map<String, AboutEntity> = (coreEntries + libraryEntries).associateBy { it.id }

    val cores: List<AboutEntity>
        get() = coreEntries

    val libraries: List<AboutEntity>
        get() = libraryEntries

    private val generatedVersionResolver: (String) -> String? = { entityId ->
        try {
            val clazz = Class.forName("com.flopster101.siliconplayer.GeneratedAboutVersions")
            val method = clazz.getMethod("versionForId", String::class.java)
            method.invoke(null, entityId) as? String
        } catch (_: Throwable) {
            null
        }
    }

    fun resolveVersion(entityId: String): String? {
        return generatedVersionResolver(entityId)
    }

    private val generatedLicenseTextResolver: (String) -> String? = { entityId ->
        try {
            val clazz = Class.forName("com.flopster101.siliconplayer.GeneratedLicenseTexts")
            val method = clazz.getMethod("textForId", String::class.java)
            method.invoke(null, entityId) as? String
        } catch (_: Throwable) {
            null
        }
    }

    val applicationEntity = AboutEntity(
        id = "app.siliconplayer",
        kind = AboutEntityKind.Library,
        name = "Silicon Player",
        description = "Open-source, multi-format chiptune, tracker and modern music player.",
        author = "Nahuel Gomez (Flopster101) and contributors",
        license = "GPL-3.0",
        links = listOf(
            AboutEntityLink("GitHub", "https://github.com/SiliconPlayer/SiliconPlayer")
        )
    )

    fun resolveLicenseText(entityId: String): String? {
        val resolved = generatedLicenseTextResolver(entityId)
        if (!resolved.isNullOrBlank()) {
            return resolved
        }
        if (entityId == "app.siliconplayer") {
            return GPL_V3_FALLBACK_TEXT
        }
        return null
    }

    private const val GPL_V3_FALLBACK_TEXT = """                    GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.

                            Preamble

  The GNU General Public License is a free, copyleft license for
software and other kinds of works.

  The licenses for most software and other practical works are designed
to take away your freedom to share and change the works.  By contrast,
the GNU General Public License is intended to guarantee your freedom to
share and change all versions of a program--to make sure it remains free
software for all its users.  We, the Free Software Foundation, use the
GNU General Public License for most of our software; it applies also to
any other work released this way by its authors.  You can apply it to
your programs, too.

  When we speak of free software, we are referring to freedom, not price.
Our General Public Licenses are designed to make sure that you have
the freedom to distribute copies of free software (and charge for them
if you wish), that you receive source code or can get it if you want
it, that you can change the software or use pieces of it in new
free programs; and that you know you can do these things.
"""

    fun resolveCoreForPlugin(pluginName: String): AboutEntity? {
        val canonicalName = canonicalDecoderNameForAlias(pluginName) ?: pluginName
        val id = pluginNameToCoreId[canonicalName] ?: return null
        return entityById[id]
    }
}
