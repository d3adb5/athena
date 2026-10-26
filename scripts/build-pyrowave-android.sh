#!/usr/bin/env bash
set -euo pipefail

# Build exactly the API revision used by Hermes' encoder and Hestia desktop.
pyrowave_revision=d2997ac172bdc00e29c58e3f2938acb7e94580bf
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_root="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
ndk_root="${ANDROID_NDK_HOME:-${sdk_root}/ndk/27.0.12077973}"
toolchain="${ndk_root}/build/cmake/android.toolchain.cmake"
[[ -f "$toolchain" ]] || { echo "Missing NDK toolchain: $toolchain" >&2; exit 1; }
work="${repo_root}/build/pyrowave-sdk"
source_dir="${work}/src"
output="${repo_root}/app/src/main/jni/pyrowave/arm64-v8a"

fetch_commit() {
    local directory=$1 remote=$2 revision=$3
    if [[ ! -d "${directory}/.git" ]]; then
        git init -q "$directory"
    fi
    if [[ "$(git -C "$directory" rev-parse HEAD 2>/dev/null || true)" != "$revision" ]]; then
        git -C "$directory" fetch --depth 1 "$remote" "$revision"
        git -C "$directory" checkout --detach FETCH_HEAD
    fi
}

mkdir -p "$work" "$output/include"
fetch_commit "$source_dir" https://github.com/Themaister/pyrowave.git "$pyrowave_revision"
granite_revision="$(sed -n 's/^GRANITE_COMMIT=//p' "$source_dir/checkout_granite.sh")"
[[ "$granite_revision" =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid pinned Granite revision' >&2; exit 1; }
fetch_commit "$source_dir/Granite" https://github.com/Themaister/Granite.git "$granite_revision"
git -C "$source_dir/Granite" submodule update --init --depth 1 \
    third_party/volk third_party/khronos/vulkan-headers

cmake -S "$source_dir" -B "${work}/arm64-v8a" \
    -DCMAKE_TOOLCHAIN_FILE="$toolchain" -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 -DANDROID_STL=c++_shared \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
    -DCMAKE_BUILD_TYPE=Release -DPYROWAVE_DEVEL=OFF -DPYROWAVE_UTILS=OFF
cmake --build "${work}/arm64-v8a" --target pyrowave-shared --parallel "${CMAKE_BUILD_PARALLEL_LEVEL:-2}"
# Android's CMake platform uses an unversioned .so SONAME. Verify it before
# packaging, because Android cannot load a missing versioned dependency.
library="${work}/arm64-v8a/libpyrowave-shared.so"
readelf_tool="${ndk_root}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
"$readelf_tool" -d "$library" | tee "${work}/dynamic.txt"
grep -Fq '[libpyrowave-shared.so]' "${work}/dynamic.txt"
if grep -Eq '\[libpyrowave-shared\.so\.[0-9]' "${work}/dynamic.txt"; then
    echo 'Versioned PyroWave SONAME cannot be packaged in an Android APK' >&2
    exit 1
fi
cp "$library" "$output/libpyrowave-shared.so"
cp "$source_dir/pyrowave.h" "$output/include/"
cp -R "$source_dir/Granite/third_party/khronos/vulkan-headers/include/vulkan" "$output/include/"
if [[ -d "$source_dir/Granite/third_party/khronos/vulkan-headers/include/vk_video" ]]; then
    cp -R "$source_dir/Granite/third_party/khronos/vulkan-headers/include/vk_video" "$output/include/"
fi
printf '%s\n' "$pyrowave_revision" > "$output/REVISION"
mkdir -p "$repo_root/app/src/main/assets/third_party"
cp "$source_dir/LICENSE" "$repo_root/app/src/main/assets/third_party/pyrowave-LICENSE.txt"
echo "PyroWave arm64 SDK ready; build with -PhestiaPyrowave=true"
