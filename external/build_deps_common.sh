#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ABSOLUTE_PATH="$SCRIPT_DIR"
MBEDTLS_DIR="$ABSOLUTE_PATH/mbedtls"
NPROC="$(nproc 2>/dev/null || getconf _NPROCESSORS_ONLN || echo 4)"
DEP_WARN_FLAGS="-w"
DEP_OPT_FLAGS="$DEP_WARN_FLAGS -Ofast"

ALL_DEPENDENCY_LIBS=(
    libsoxr
    mbedtls
    ffmpeg
    libopenmpt
    libxmp
    libayfly
    ufmod
    libvgm
    libgme
    libresid
    libresidfp
    libsidplayfp
    crsid
    lazyusf2
    psflib
    vio2sf
    fluidsynth
    sc68
    libbinio
    adplug
    libzakalwe
    bencodetools
    vasm
    uade
    hivelytracker
    klystrack
    furnace
    projectm
    dnfamitracker
    libupse
    viogsf
    nezplugpp
)

# Aliases documented here, shared by all build_deps usage screens.
LIB_ALIASES_HINT="sox/soxr, gme, xmp, ayfly, resid/residfp, sid/sidplayfp, crsid/cRSID/libcrsid, usf/lazyusf, psf, 2sf/twosf, fluid/libfluidsynth, libsc68, binio, libadplug, zakalwe, bencode, assembler/vasm, libuade, hvl/hively, kly/kt, fur, dnfamitracker/dnft, upse/libupse, viogsf, nezplugpp"

# Comma-separated "all,<libs...>" for usage text.
all_libs_csv() {
    local IFS=,
    echo "all,${ALL_DEPENDENCY_LIBS[*]}"
}

detect_linux_family() {
    if [ ! -r /etc/os-release ]; then
        echo "unknown"
        return
    fi

    # shellcheck disable=SC1091
    . /etc/os-release
    local id="${ID:-}"
    local id_like="${ID_LIKE:-}"

    case "$id" in
        ubuntu|debian)
            echo "debian"
            ;;
        arch|manjaro|endeavouros)
            echo "arch"
            ;;
        *)
            if [[ "$id_like" == *debian* ]]; then
                echo "debian"
            elif [[ "$id_like" == *arch* ]]; then
                echo "arch"
            else
                echo "unknown"
            fi
            ;;
    esac
}

install_dependency_if_missing() {
    local dep_name="$1"
    local binary_check="$2"
    local debian_pkg="$3"
    local arch_pkg="$4"
    local linux_family="$5"

    local bin
    IFS='|' read -r -a _bin_candidates <<< "$binary_check"
    for bin in "${_bin_candidates[@]}"; do
        if command -v "$bin" >/dev/null 2>&1; then
            echo "$dep_name already installed."
            return 0
        fi
    done

    if command -v "$binary_check" >/dev/null 2>&1; then
        echo "$dep_name already installed."
        return 0
    fi

    echo "$dep_name missing. Attempting installation for family: $linux_family"
    case "$linux_family" in
        debian)
            sudo apt-get update && sudo apt-get install -y "$debian_pkg"
            ;;
        arch)
            sudo pacman -Sy --noconfirm "$arch_pkg"
            ;;
        *)
            echo "Warning: unsupported distro. Cannot auto-install $dep_name."
            ;;
    esac
}

ensure_system_dependencies() {
    local linux_family
    linux_family="$(detect_linux_family)"
    install_dependency_if_missing "XA assembler" "xa|xa65" "xa65" "xa" "$linux_family"
}

apply_libsidplayfp_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libsidplayfp"
    if [ ! -d "$PROJECT_PATH" ]; then
        return
    fi

    if grep -Fq "class SID_EXTERN c64sid" "$PROJECT_PATH/src/c64/c64sid.h"; then
        echo "libsidplayfp patch already applied."
        return
    fi

    echo "Applying libsidplayfp patch: SID_EXTERN visibility"
    local patch_file
    patch_file="$(mktemp)"
    cat > "$patch_file" <<'PATCH'
diff --git a/src/c64/Banks/Bank.h b/src/c64/Banks/Bank.h
--- a/src/c64/Banks/Bank.h
+++ b/src/c64/Banks/Bank.h
@@ -32,7 +32,7 @@ namespace libsidplayfp
 /**
  * Base interface for memory and I/O banks.
  */
-class Bank
+class SID_EXTERN Bank
 {
 public:
     /**
diff --git a/src/c64/c64sid.h b/src/c64/c64sid.h
--- a/src/c64/c64sid.h
+++ b/src/c64/c64sid.h
@@ -23,6 +23,7 @@
 
 #include "Banks/Bank.h"
 
+#include "sidplayfp/siddefs.h"
 #include "sidcxx11.h"
 
 #include <algorithm>
@@ -36,7 +37,7 @@ namespace libsidplayfp
 /**
  * SID interface.
  */
-class c64sid : public Bank
+class SID_EXTERN c64sid : public Bank
 {
 private:
     uint8_t lastpoke[0x20];
diff --git a/src/sidemu.h b/src/sidemu.h
--- a/src/sidemu.h
+++ b/src/sidemu.h
@@ -43,7 +43,7 @@ namespace libsidplayfp
 /**
  * Inherit this class to create a new SID emulation.
  */
-class sidemu : public c64sid
+class SID_EXTERN sidemu : public c64sid
 {
 private:
     sidbuilder* const m_builder;
diff --git a/src/sidplayfp/sidbuilder.h b/src/sidplayfp/sidbuilder.h
--- a/src/sidplayfp/sidbuilder.h
+++ b/src/sidplayfp/sidbuilder.h
@@ -27,6 +27,7 @@
 #include <string>
 
 #include "sidplayfp/SidConfig.h"
+#include "sidplayfp/siddefs.h"
 
 namespace libsidplayfp
 {
@@ -37,7 +38,7 @@ class EventScheduler;
 /**
  * Base class for sid builders.
  */
-class sidbuilder
+class SID_EXTERN sidbuilder
 {
 protected:
     typedef std::set<libsidplayfp::sidemu*> emuset_t;
PATCH

    if ! git -C "$PROJECT_PATH" apply "$patch_file"; then
        echo "Error applying libsidplayfp patch."
        rm -f "$patch_file"
        exit 1
    fi
    rm -f "$patch_file"
}

build_vasm_host() {
    echo "Building vasm host tool..."
    local PROJECT_PATH="$ABSOLUTE_PATH/vasm"
    if [ ! -d "$PROJECT_PATH" ]; then
        echo "Error: vasm source not found at $PROJECT_PATH."
        return 1
    fi

    if [ "${FORCE_CLEAN:-0}" -eq 1 ]; then
        (
            cd "$PROJECT_PATH"
            make --no-print-directory clean >/dev/null 2>&1 || true
        )
    fi

    if [ -x "$PROJECT_PATH/vasmm68k_mot" ]; then
        echo "vasm host tool already built -> skipping"
        return 0
    fi

    (
        cd "$PROJECT_PATH"
        make --no-print-directory -j"$NPROC" CPU=m68k SYNTAX=mot
    )

    if [ ! -x "$PROJECT_PATH/vasmm68k_mot" ]; then
        echo "Error: vasm build succeeded but vasmm68k_mot is missing."
        return 1
    fi
}

normalize_lib_name() {
    local lib="$1"
    case "$lib" in
        mbedtls|libmbedtls)
            echo "mbedtls"
            ;;
        sox|soxr)
            echo "libsoxr"
            ;;
        gme)
            echo "libgme"
            ;;
        xmp)
            echo "libxmp"
            ;;
        ayfly|libayfly)
            echo "libayfly"
            ;;
        resid)
            echo "libresid"
            ;;
        residfp)
            echo "libresidfp"
            ;;
        sid|sidplayfp)
            echo "libsidplayfp"
            ;;
        crsid|cRSID|libcrsid|libcRSID)
            echo "crsid"
            ;;
        usf|lazyusf|lazyusf2)
            echo "lazyusf2"
            ;;
        psf|psflib)
            echo "psflib"
            ;;
        2sf|twosf|vio2sf)
            echo "vio2sf"
            ;;
        fluid|fluidsynth|libfluidsynth)
            echo "fluidsynth"
            ;;
        sc68|libsc68)
            echo "sc68"
            ;;
        binio|libbinio)
            echo "libbinio"
            ;;
        adplug|libadplug)
            echo "adplug"
            ;;
        zakalwe|libzakalwe)
            echo "libzakalwe"
            ;;
        bencode|bencodetools|libbencodetools)
            echo "bencodetools"
            ;;
        assembler|vasm)
            echo "vasm"
            ;;
        uade|libuade)
            echo "uade"
            ;;
        hvl|hively|hivelytracker|libhivelytracker)
            echo "hivelytracker"
            ;;
        kly|kt|klystrack|libklystrack)
            echo "klystrack"
            ;;
        fur|furnace|libfurnace)
            echo "furnace"
            ;;
        projectm|projectM|libprojectm|libprojectM)
            echo "projectm"
            ;;
        dnfamitracker|libdnfamitracker|famitracker|dn-famitracker|dnft)
            echo "dnfamitracker"
            ;;
        upse|libupse)
            echo "libupse"
            ;;
        *)
            echo "$lib"
            ;;
    esac
}

target_has_lib() {
    local needle="$1"
    local raw_target="$TARGET_LIB"

    if [ "$raw_target" = "all" ]; then
        return 0
    fi

    IFS=',' read -r -a _libs <<< "$raw_target"
    for item in "${_libs[@]}"; do
        local normalized
        normalized="$(normalize_lib_name "$item")"
        if [ "$normalized" = "$needle" ]; then
            return 0
        fi
    done
    return 1
}

dep_source_rev() {
    local project_path="$1"
    local rev
    rev=$(git -C "$project_path" rev-parse HEAD 2>/dev/null) || return 1
    if ! git -C "$project_path" diff --quiet HEAD 2>/dev/null || \
       ! git -C "$project_path" diff --cached --quiet 2>/dev/null; then
        rev="${rev}-dirty"
    fi
    echo "$rev"
}

dep_source_stamp_matches() {
    local install_dir="$1"
    local lib="$2"
    local project_path="$3"
    local stamp="$install_dir/lib/.${lib}_gitrev"
    [ -f "$stamp" ] || return 1
    local rev
    rev=$(dep_source_rev "$project_path") || return 0
    [ "$(cat "$stamp" 2>/dev/null)" = "$rev" ]
}

dep_write_source_stamp() {
    local install_dir="$1"
    local lib="$2"
    local project_path="$3"
    local rev
    rev=$(dep_source_rev "$project_path") || return 0
    mkdir -p "$install_dir/lib"
    echo "$rev" > "$install_dir/lib/.${lib}_gitrev"
}

is_valid_lib() {
    local lib="$1"
    local entry
    local normalized
    normalized="$(normalize_lib_name "$lib")"
    for entry in all "${ALL_DEPENDENCY_LIBS[@]}"; do
        if [ "$normalized" = "$entry" ]; then
            return 0
        fi
    done
    return 1
}
