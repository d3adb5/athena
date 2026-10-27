#!/bin/bash
# Cross-compiles the DeepFilterNet3 JNI bridge (src/lib.rs, a Rust crate) for each ABI via cargo-ndk
# into <out>/libs/<abi>/libdeepfilter.so. Up-to-date ABIs are skipped. Needs the Android Rust targets
# (rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android)
# and cargo-ndk (cargo install cargo-ndk).
#
# Usage: build.sh <ndk-dir> <out-dir>
set -euo pipefail

NDK="$1"
OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"

if [ ! -f "$HERE/DeepFilterNet/libDF/Cargo.toml" ]; then
    echo "DeepFilterNet missing; run: git submodule update --init" >&2
    exit 1
fi
if ! cargo ndk --version >/dev/null 2>&1; then
    echo "cargo-ndk not found; install it with: cargo install cargo-ndk" >&2
    exit 1
fi

# Host flags (e.g. -march=native) must not reach the cross build
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS
export ANDROID_NDK_HOME="$NDK"
export CARGO_TARGET_DIR="$OUT/target"
export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"

# 32-bit ABIs need API 24 (64-bit file APIs in Rust's std and tract)
declare -A API=([arm64-v8a]=21 [armeabi-v7a]=24 [x86]=24 [x86_64]=21)

for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    SO="$OUT/libs/$ABI/libdeepfilter.so"
    if [ -f "$SO" ] && [ "$SO" -nt "$HERE/src/lib.rs" ] && [ "$SO" -nt "$HERE/Cargo.toml" ] && [ "$SO" -nt "$0" ]; then
        continue
    fi
    (cd "$HERE" && cargo ndk -t "$ABI" -P "${API[$ABI]}" -o "$OUT/libs" build --release --locked)
    touch "$SO"
done
