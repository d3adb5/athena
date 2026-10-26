# PyroWave Android integration

This branch adds the optional PyroWave SDR YUV 4:2:0 codec to Hestia Android.
It preserves the current Android common-c transport (microphone, S Pen and
input extensions) and ports only the desktop codec negotiation and independent
frame recovery. Automatic mode continues to use H.264/HEVC/AV1.

## Build and test

```sh
git submodule update --init --recursive
export ANDROID_HOME=/path/to/android-sdk
bash scripts/build-pyrowave-android.sh
./gradlew :app:assembleNonRoot_gameDebug :app:testNonRoot_gameDebugUnitTest -PhestiaPyrowave=true
```

The opt-in SDK is pinned to PyroWave d2997ac172bdc00e29c58e3f2938acb7e94580bf
(C API 0.5), matching Hermes. It builds for arm64-v8a; other ABIs retain codec
stubs and ordinary MediaCodec support. A build without the Gradle property
does not depend on PyroWave. JNI probing rejects unavailable Vulkan devices.

The `PyroWave Android` Actions workflow compiles the SDK and APKs, runs JVM
lifecycle tests and verifies the arm64 APK contains the codec and JNI exports.
It supplies installable debug artifacts without publishing a stable release.

## Host and client settings

Use Hermes `release/october-integration` or `feature/pyrowave-codec` built with
PyroWave. Enable its experimental PyroWave setting. On Android choose
`PyroWave (Experimental, SDR LAN)` in the video codec setting and disable HDR.
Start with 1280x720 at 60 FPS on a high-bandwidth LAN, then test 1080p and the
desired bitrate. The initial Android decoder accepts even dimensions up to
4096 by 4096. A host that does not advertise PyroWave is rejected before launch
with an explanation; select another codec to use a legacy host.

Wire constants agree with desktop: ServerCodecModeSupport 0x40000000,
VIDEO_FORMAT 0x01000000, RTSP `PYROWAVE/90000`, bitStreamFormat 64.
Frames use exact lengths and no H.264/HEVC parsing. A damaged or missing frame
does not prevent the following independent frame from decoding.

## Current performance boundary

Decode runs in Vulkan, reads SDR planes to CPU memory, then uploads them to
GLES textures for Rec.709 limited-range presentation on the Android Surface.
This is a functional baseline like the current desktop decoder. It is not
zero-copy and has no claim of improved device latency, power use or frame rate.
The decode callback does not run on the receive thread. The native EGL context
is released after each frame so common-c can safely set up, decode and clean up
on different threads; Java serializes native use and destruction.

Before merging for the October release, validate on physical Adreno and Mali
devices: picture orientation and Rec.709 colors; 720p/1080p at 60 FPS; GPU/CPU
time and temperature; packet loss recovery; repeated stop/reconnect; screen
rotation/backgrounding; switching back to legacy codecs; HDR rejection; DS4
and S Pen input. Measure against HEVC at the same resolution and FPS.
GPU decode/Surface presentation and these hardware checks cannot be established
by JVM or headless build success alone.

## Remaining release gates

- Obtain a passing optional-SDK Android build and install its artifact.
- Validate the complete Hermes + desktop/Android path with real frames.
- Compare image quality, bandwidth, frame time and power against legacy codecs.
- Test the combined host with Gamescope, reconnect, private audio/microphone,
  two concurrent sessions and libvirtualhid controllers without input leakage.
- Keep release APK signing/versioning and existing package identities intact.

Direct Vulkan presentation can remove readback and texture uploads in a later
step; the explicit build/runtime gates allow validation of the baseline first.
