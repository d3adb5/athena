# Athena

Athena is an Android client for [Ares](https://github.com/d3adb5/ares) (a fork of
[Hermes](https://github.com/MrOz59/hermes), which it also works with), Apollo and Sunshine. It is a fork of
[Artemis](https://github.com/ClassicOldSong/moonlight-android) (Moonlight Noir) built for the Retroid Pocket 6 and
other Android handhelds, and carries every feature of [Hestia](https://github.com/MrOz59/hestia-android).

It keeps the Artemis 20.2.6-era video path. ClassicOldSong's later frame pacing rework (latest-frame rendering,
unified decoder latency, strict vsync, MediaTek/Qualcomm low-latency flags) regresses on the Snapdragon 8 Gen 2, so
none of it is here.

Athena's package is `local.retroid.athena` (`.root` for the root build, `.debug` for debug builds). It installs
beside Artemis (`com.limelight.noir`), Artemis RP (`local.retroid.artemis`) and Hestia (`com.mroz59.hestia`). Each is
a separate client to the host, so pair Athena on its own.

## What it adds to Artemis

From Hestia:

- Ares session preparation: "Request isolated session" asks the host for a session of its own and sends this
  device's resolution, frame rate, bitrate and HDR before launch, with the reason shown when Ares can't. How it
  isolates (Gamescope, KWin) is the host's setting; on the wire this is still `virtualDisplay=1`.
- Ares status in the host list, the app list and at launch (encoder, display, permissions, last session), with a
  "Restore display" action and a diagnostic report you can share from the PC menu.
- Stream info for a few seconds after connecting: what Ares captures, the encoder, the GPU, size, rate, codec and
  latency.
- Microphone streaming to hosts that support it, the mouse wiggle on connect as a setting.
- PyroWave, an optional Vulkan wavelet codec (see below).
- More reliable pairing (no replayed challenges, recovery after a wrong PIN, bounded PIN waits), manual IPv6
  addresses with ports, a direct mDNS fallback when Android's discovery stalls, a fix for late discovery callbacks.
- S Pen and stylus input, an Android TV layout in the same APK, settings categories that fold and unfold with the
  D-pad, no global keyboard accessibility service, no phone or storage permissions.

Its own:

- The Retroid Pocket's built-in gamepad counts as internal, so its Back button opens the game menu.
- Microphone echo cancellation, noise suppression (Standard or DeepFilterNet3) and gain control; a microphone
  quality setting (24-128 kbps). It never captures from `UNPROCESSED`, which the RP6 routes to an unwired mic.
- Settings > Host ports (below).

## Ports and protocols

Everything Athena connects to on a host. Settings > Host ports changes each of them for every host; leave a field
blank to use the port the host names, or the default. Set one when a router forwards a port somewhere else. A
change takes effect on the next connection.

| Setting | Protocol | Default | Used for | Blank means |
|---|---|---|---|---|
| HTTP | TCP | 47989 | Host info, pairing (plain HTTP) | 47989, for an address typed without a port |
| HTTPS | TCP | 47984 | App list, launch, quit (TLS, pinned host certificate) | The port in the host's `serverinfo` |
| Ares API | TCP | 47990 | Hestia v1 API: capabilities, permissions, session prepare, status, display restore, stream info, diagnostic report (HTTPS, same pinned identity) | HTTPS + 6 |
| RTSP | TCP | 48010 | Stream setup | The port in the host's launch reply |
| Video | UDP | 47998 | Video packets | The port in the host's RTSP SETUP reply |
| Control | UDP | 47999 | ENet control stream, which also carries input | The host's RTSP SETUP reply |
| Audio | UDP | 48000 | Audio packets | The host's RTSP SETUP reply |
| Microphone | UDP | 47996 | Microphone packets, only while the mic is streamed | The host's RTSP SETUP reply |
| Wake-on-LAN | UDP | 9, 47009 | Magic packet | (a list; blank sends none to these) |

Not configurable:

- Wake-on-LAN also goes to 47998, 47999, 48000, 48002 and 48010, shifted by the host's HTTP port (as Moonlight
  does), to each known address and to the broadcast address.
- Discovery uses mDNS (`_nvstream._tcp`, UDP 5353 multicast), fixed by the protocol.
- Two connections don't go to the host: STUN to `stun.moonlight-stream.org` UDP 3478 to learn the external address,
  and the network test in the connection error dialog (Moonlight's test server).

The stream ports reach moonlight-common-c through `LiSetPortOverrides` (our fork,
[d3adb5/athena-core](https://github.com/d3adb5/athena-core), branch `athena`).

## PyroWave

PyroWave sends every frame on its own (no motion prediction), with a wavelet transform that encodes and decodes in
a fraction of a millisecond. The price is size: for the same picture its frames are 4-5 times bigger than HEVC's.
Whether it lowers latency depends on whether the time saved encoding and decoding beats the extra time the bigger
frame spends on Wi-Fi.

In short, on the Retroid Pocket 6 over Wi-Fi, PyroWave makes the stream slower, by about 1.5-3 ms per frame. With
the Wi-Fi receive interrupts moved to the big cores it comes out about even. It does handle packet loss better: a
lost packet blurs one small block instead of stalling the picture until the stream recovers. It is a clear win only
on a wired or very fast link.

How we got there (1080p at 120 fps, host RX 7900 GRE, client Snapdragon 8 Gen 2):

Δ = (PyroWave frame − HEVC frame) / Wi-Fi rate − encode saving − decode saving (positive: PyroWave is slower)

| Input | Value | Source |
|---|---|---|
| HEVC frame | 0.42 Mbit (50 Mbit/s) | |
| PyroWave frame | 2.07 Mbit per bit per pixel | 0.8 bpp ≈ its usable floor, 1.5 bpp ≈ artefacts hard to see |
| RP6 Wi-Fi receive | ~350 Mbit/s; ~700 with RX interrupts on the big cores | measured, single stream, 6 GHz |
| Encode saving | ~1.05 ms (HEVC ~1.2 ms, PyroWave ~0.15 ms) | measured HEVC; PyroWave's published 0.08-0.13 ms |
| Decode saving | −1 to +3.5 ms, ~1 ms likely | HEVC measured 2-5 ms; PyroWave on Adreno 740 estimated 1.5-3 ms |

| PyroWave quality | Stream rate | Stock Wi-Fi | RX interrupts on big cores |
|---|---|---|---|
| 0.8 bpp | 199 Mbit/s | +1.5 ms (−1.0 to +3.5) | −0.3 ms (−2.8 to +1.7) |
| 1.0 bpp | 249 Mbit/s | +2.7 ms (+0.2 to +4.7) | +0.3 ms (−2.2 to +2.3) |
| 1.5 bpp | 373 Mbit/s | doesn't fit | +1.8 ms (−0.7 to +3.8) |

Break-even is 0.55 bpp (137 Mbit/s) on stock Wi-Fi and 0.89 bpp (222 Mbit/s) with the interrupt fix. At 1 bpp the
stream keeps the stock link 71% busy, so Wi-Fi retries spill into the next frame as jitter. At a handheld's
viewing distance (the RP6's 6.9 cm tall panel at ~30 cm), ~0.8 bpp should already look clean.

What is not measured yet: PyroWave's decode time on the Adreno 740. Athena's PyroWave decoder copies each frame from
Vulkan to the CPU and uploads it to OpenGL ES (no zero-copy yet), and that cost decides the result. To measure it,
stream the same game at 1080p120 with HEVC and with PyroWave and compare the decode time in the performance overlay,
then put it in the formula.

To use PyroWave: build Athena with it (below), run an Ares built with PyroWave and `pyrowave = enabled`, turn HDR
off, and pick "PyroWave (Experimental, SDR LAN)" as the video codec. A host that doesn't offer it is refused before
launch.

## Building

- Android SDK with NDK 27.0.12077973, JDK 21.
- `git submodule update --init --recursive`
- Microphone noise suppression (DeepFilterNet3) needs Rust with the Android targets and cargo-ndk:
  `rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android`,
  `cargo install cargo-ndk`.
- `local.properties`: `sdk.dir=`, and optionally `signing.storeFile`, `signing.storePassword`, `signing.keyAlias`
  for a signed release.
- `./gradlew assembleNonRoot_gameRelease` → `app/build/outputs/apk/nonRoot_game/release/`
- With PyroWave (arm64 only): `ANDROID_HOME=<sdk> bash scripts/build-pyrowave-android.sh`, then
  `./gradlew assembleNonRoot_gameRelease -PhestiaPyrowave=true`. Details: [docs/pyrowave.md](docs/pyrowave.md).
- Unit tests: `./gradlew testNonRoot_gameReleaseUnitTest`

## Credits

Athena builds on [Moonlight](https://github.com/moonlight-stream/moonlight-android) (Cameron Gutman, Diego
Waxemberg, Aaron Neyer, Andrew Hennessy, started at Case Western and MHacks),
[Artemis](https://github.com/ClassicOldSong/moonlight-android) by ClassicOldSong,
[Hestia](https://github.com/MrOz59/hestia-android) by MrOz59 and [PyroWave](https://github.com/Themaister/pyrowave) by
Hans-Kristian Arntzen.
