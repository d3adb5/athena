#!/bin/bash
# Cross-compiles webrtc-audio-processing (Meson, with its abseil subproject) for each ABI and
# links it with webrtc_apm.cpp into <out>/libs/<abi>/libwebrtcapm.so. Kept out of ndk-build
# because the library is a Meson project; up-to-date ABIs are skipped.
#
# Usage: build.sh <ndk-dir> <out-dir>
set -euo pipefail

NDK="$1"
OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
WAP="$HERE/webrtc-audio-processing"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [ ! -f "$WAP/meson.build" ]; then
    echo "webrtc-audio-processing missing; run: git submodule update --init" >&2
    exit 1
fi

# Host flags (e.g. -march=native) must not reach the cross build
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS

# 32-bit ABIs need API 24: Meson forces _FILE_OFFSET_BITS=64, and libc++ then needs fseeko
declare -A API=([arm64-v8a]=21 [armeabi-v7a]=24 [x86]=24 [x86_64]=21)
declare -A TRIPLE=([arm64-v8a]=aarch64-linux-android [armeabi-v7a]=armv7a-linux-androideabi
                   [x86]=i686-linux-android [x86_64]=x86_64-linux-android)
declare -A FAMILY=([arm64-v8a]=aarch64 [armeabi-v7a]=arm [x86]=x86 [x86_64]=x86_64)

for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    SO="$OUT/libs/$ABI/libwebrtcapm.so"
    if [ -f "$SO" ] && [ "$SO" -nt "$HERE/webrtc_apm.cpp" ] && [ "$SO" -nt "$0" ]; then
        continue
    fi
    CC="$TC/${TRIPLE[$ABI]}${API[$ABI]}-clang"
    BUILD="$OUT/$ABI"
    CROSS="$OUT/$ABI.ini"
    mkdir -p "$OUT/libs/$ABI"
    cat > "$CROSS" <<EOF
[binaries]
c = '$CC'
cpp = '$CC++'
ar = '$TC/llvm-ar'
strip = '$TC/llvm-strip'
[host_machine]
system = 'android'
cpu_family = '${FAMILY[$ABI]}'
cpu = '${FAMILY[$ABI]}'
endian = 'little'
EOF
    if [ ! -f "$BUILD/build.ninja" ]; then
        meson setup "$BUILD" "$WAP" --cross-file "$CROSS" --buildtype=release \
            --wrap-mode=forcefallback -Ddefault_library=static
    fi
    ninja -C "$BUILD"

    ABSEIL="$(find "$WAP/subprojects" -maxdepth 1 -type d -name 'abseil-cpp-*' | head -1)"
    "$CC++" -std=c++17 -fPIC -O2 -c "$HERE/webrtc_apm.cpp" -o "$BUILD/webrtc_apm.o" \
        -I"$WAP/webrtc" -I"$ABSEIL"
    # shellcheck disable=SC2046
    "$CC++" -shared -fPIC -o "$SO" "$BUILD/webrtc_apm.o" \
        -Wl,--start-group $(find "$BUILD" -name '*.a') -Wl,--end-group \
        -static-libstdc++ -llog -lm \
        -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,-z,max-page-size=16384
    "$TC/llvm-strip" "$SO"
done
