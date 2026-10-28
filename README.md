# Miru (見る)

Native Android anime streaming application built with Kotlin Jetpack Compose and an embedded Rust daemon.

[![Release](https://img.shields.io/github/v/release/David4rr/miru?style=flat-square&color=FF6A00&label=Release)](https://github.com/David4rr/miru/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square)](LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/David4rr/miru/android-build.yml?branch=main&style=flat-square&label=Build)](https://github.com/David4rr/miru/actions/workflows/android-build.yml)
[![GitHub Stars](https://img.shields.io/github/stars/David4rr/miru?style=flat-square&logo=github&color=e3b341&label=Stars)](https://github.com/David4rr/miru/stargazers)

[Download Android APK](https://github.com/David4rr/miru/releases/latest/download/app-release.apk)

---

## Overview

Miru couples a declarative Jetpack Compose interface with a background Rust daemon running locally on the device. Network requests, video playlist parsing, segment proxying, and streaming circumvention run within compiled native code, while the Android layer handles presentation and hardware-accelerated video decoding.

### Core Architecture

- **Client Layer:** Kotlin Jetpack Compose running against Android Media3 ExoPlayer for video playback and Coil for asynchronous image caching. The interface uses a dark OLED color scheme (`#0D0E11`) with hardware display refresh synchronization (up to 120 Hz).
- **Daemon Layer (`libmiru_core.so`):** Embedded Tokio and Axum HTTP service bound to `127.0.0.1:8767`. The daemon manages AniList GraphQL metadata queries, upstream stream resolution, M3U8 manifest rewriting, video chunk proxying, anti-DPI TLS record fragmentation, and SQLite persistence.

---

## Attribution and Lineage

Miru is an independent fork and architectural rewrite inspired by **[Tatap](https://github.com/asfaraguest/tatap)**, created by **Bagus Azizul** ([@asfaraguest](https://github.com/asfaraguest)).

The upstream Tatap project implemented anime streaming using a Python Chaquopy backend and Android Java/XML views. Miru retains the conceptual foundation and source resolvers while replacing the implementation:
- The Python Chaquopy runtime is replaced by a compiled native Rust daemon using Tokio and Axum, reducing memory consumption to under 30 MB during playback and enabling persistent HTTP/2 transport.
- The Android presentation layer is rebuilt in Kotlin Jetpack Compose, introducing scroll parallax headers, sticky morphing top bars, gestures for volume and brightness, and edge-to-edge window insets.
- Storage and network routines run through SQLite and local HTTP proxying with TLS ClientHello record fragmentation.

Credit for the original concept and resolvers belongs to Bagus Azizul and the Tatap contributors.
- **Upstream Repository:** [https://github.com/asfaraguest/tatap](https://github.com/asfaraguest/tatap)
- **Support Upstream Author:** [Trakteer (Bagus Azizul)](https://trakteer.id/bagus_azizul) | [Ko-fi](https://ko-fi.com/L5J028GNTA)

---

## Key Components

### Video Playback and Controls
- Media3 ExoPlayer integration with low-latency buffering (`bufferForPlaybackMs = 500`).
- Touch gesture controls: vertical swipe on screen edges for brightness and volume adjustments, and double-tap seeking (+10s / -10s).
- In-player episode picker modal sheet and playback state synchronization across app sessions.
- Subtitle parsing via local proxy with HTTP Referer header injection and fallback handling.

### Interface and Navigation
- Edge-to-edge layout using strict system insets (`statusBarsPadding()` and `navigationBarsPadding()`) across all views.
- Parallax hero backdrop headers on home and detail views with scroll offset tracking.
- Pinned top bar in anime details that transitions from transparent to solid obsidian upon scrolling.
- Floating action button navigation bar that automatically conceals during list scrolling and reappears when idle.
- Chronological franchise navigation showing release order and format badges across seasons.
- Recommendation engine that scores unwatched catalog titles against user watch history and genre affinities.

### Network and Streaming Engine
- Embedded Axum HTTP service running over loopback (`127.0.0.1:8767`).
- Anti-DPI transport engine: splits TLS ClientHello records across TCP packets to bypass deep packet inspection on restricted networks.
- DNS-over-HTTPS (DoH) resolver querying Cloudflare and Google upstream endpoints.
- M3U8 manifest rewriter: converts absolute remote chunk URLs into local proxy routes to inject required authentication and referer headers into MPEG-TS requests.
- Local SQLite database handling bookmarks, search history, and watch progress timestamps.

### Application Lifecycle and Maintenance
- Background playback protection via Android lifecycle observers, releasing player resources when paused or stopped.
- In-app update system checking GitHub Releases API, downloading APK payloads, and triggering the system package installer through Android FileProvider.
- Diagnostic reporting sheet generating system status summaries with direct links to the GitHub issue tracker.

---

## Repository Structure

```
miru/
├── android/            # Android application project (Kotlin, Jetpack Compose, Media3)
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/id/miru/app/  # Compose UI, viewmodels, and data layer
│   │       ├── jniLibs/           # Cross-compiled Rust shared libraries (.so)
│   │       └── res/               # Vector drawables, themes, and mipmaps
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── core/               # Native engine daemon (Rust, Tokio, Axum)
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs      # JNI bridge and daemon lifecycle entrypoints
│       ├── api/        # Stream resolvers and AniList GraphQL queries
│       ├── proxy/      # M3U8 playlist rewriter and TS segment proxy
│       ├── net/        # TLS ClientHello record fragmentation and DoH client
│       └── db/         # SQLite watch history and bookmark storage
└── .github/
    └── workflows/
        └── android-build.yml # Automated CI/CD build and release pipeline
```

---

## Building from Source

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- Java Development Kit (JDK) 17
- Rust stable toolchain with `cargo-ndk`
- Android NDK (version r26b or newer)

### 1. Build the Native Core

Compile the Rust engine for the target Android architectures and export the shared libraries to the Android project:

```bash
cd core

# Install cargo-ndk if not present
cargo install cargo-ndk

# Add Android targets
rustup target add aarch64-linux-android x86_64-linux-android

# Compile release binaries
cargo ndk -t arm64-v8a -t x86_64 -o ../android/app/src/main/jniLibs build --release
```

### 2. Build the Android Application

Compile the release APK with Gradle:

```bash
cd ../android
./gradlew assembleRelease
```

The compiled APK will be located at:
`android/app/build/outputs/apk/release/app-release.apk`

Automated cross-compilation, ProGuard optimization, and release signing are also executed via GitHub Actions on tagged releases.

---

## License

Miru is licensed under the **GNU General Public License v3.0 (GPL-3.0)**, preserving copyleft rights and attribution to upstream work. See [LICENSE](LICENSE) for the full license text.
