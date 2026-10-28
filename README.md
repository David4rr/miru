<div align="center">
  <h1>Miru (見る)</h1>
  <p><strong>Modern, high-performance native Android anime streaming client.</strong></p>
  <p>100% Kotlin Jetpack Compose UI with Crunchyroll-inspired dark OLED aesthetics, powered by an embedded native Rust streaming engine.</p>

  <p>
    <a href="https://github.com/David4rr/miru/releases/latest"><img src="https://img.shields.io/github/v/release/David4rr/miru?style=flat-square&color=FF6A00&label=Release" alt="Latest Release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License: GPL-3.0"></a>
    <a href="https://github.com/David4rr/miru/actions/workflows/android-build.yml"><img src="https://img.shields.io/github/actions/workflow/status/David4rr/miru/android-build.yml?branch=main&style=flat-square&label=Build" alt="CI Build Status"></a>
    <a href="https://github.com/David4rr/miru/stargazers"><img src="https://img.shields.io/github/stars/David4rr/miru?style=flat-square&logo=github&color=e3b341&label=Stars" alt="GitHub Stars"></a>
  </p>

  <p>
    <a href="https://github.com/David4rr/miru/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download-Android%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>
  </p>
</div>

---

## 📜 Attribution & Open-Source Etiquette

> **Fork & Heritage Notice:**  
> **Miru** is an independent, native Kotlin and embedded Rust reimagining/fork inspired by and building upon the conceptual foundation, streaming resolvers, and community spirit of **[Tatap](https://github.com/asfaraguest/tatap)** created by **Bagus Azizul** ([@asfaraguest](https://github.com/asfaraguest)).

While the original Tatap pioneered multiplatform anime streaming with a Python Chaquopy backend and Android Java/XML views, **Miru** completely re-architected the application from the ground up for modern Android:
- **Rust Engine Core:** Replaced Python Chaquopy with a compiled native Rust daemon (`libmiru_core.so`) using Tokio and Axum on `127.0.0.1:8767`, delivering near-zero memory footprint (<30 MB RAM) and persistent HTTP/2 chunk streaming.
- **100% Jetpack Compose UI:** Rebuilt the entire user interface in declarative Kotlin Jetpack Compose featuring 120 FPS motion, dynamic parallax headers, morphing sticky top bars, snappy tactile micro-interactions, and deep pitch-black OLED surfaces.
- **AniSkip Automation:** Automated intro/outro skipping with dynamic player HUD indicators and seamless chapter progression.

All credit and sincere gratitude go to **Bagus Azizul** and the **Tatap** contributors for their foundational work. Please consider supporting the upstream project:
- **Upstream Repository:** [https://github.com/asfaraguest/tatap](https://github.com/asfaraguest/tatap)
- **Support Upstream Author:** [Trakteer (Bagus Azizul)](https://trakteer.id/bagus_azizul) · [Ko-fi](https://ko-fi.com/L5J028GNTA)

---

## ✨ Features

- **Fluid 120Hz Jetpack Compose UI:** Tailored Crunchyroll-inspired dark OLED palette (`#0D0E11`), architectural hairline borders (`#26262B`), dynamic parallax backdrop translations, and morphing sticky app bars.
- **Snappy Micro-Interactions:** Tactile spring physics (`Spring.StiffnessMedium`) on action buttons and auto-hiding floating navigation pill.
- **Zero Status Bar Collisions:** Strict edge-to-edge system insets (`statusBarsPadding()`) across all tabs and modal sheets.
- **High-Throughput Native Rust Engine:** Embedded daemon (`libmiru_core.so`) running Axum and Tokio, handling video segment proxying, M3U8 rewriting, TLS ClientHello desync for anti-DPI circumvention, and DNS-over-HTTPS (DoH).
- **Accurate Airing & Franchise Sync:** Real-time AniList GraphQL catalog synchronization, airing schedules, strict episode bounds without fake placeholders, and cross-season franchise pickers.
- **Advanced Player (Media3 ExoPlayer):** Custom HUD with double-tap gesture seek pills, vertical swipe brightness & volume gestures, subtitle formatting with failover, and AniSkip intro/outro skipping.
- **In-App Auto-Update & Changelog:** Built-in APK updater with live download progress feedback and release notes viewer.

---

## 🏗️ Architecture

```
miru/
├── android/            # Android Gradle project (Kotlin, Jetpack Compose, Media3)
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/id/miru/app/  # Kotlin Compose UI & App Logic
│   │       ├── jniLibs/           # Cross-compiled Rust libraries (.so)
│   │       └── res/               # Vector drawables, themes, mipmaps
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── core/               # Embedded Rust Daemon (Axum, Tokio, Proxy, Anti-DPI)
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs      # JNI bridge & daemon entrypoints
│       ├── api/        # Stream resolvers & AniList GraphQL
│       ├── proxy/      # M3U8 playlist rewriter & TS streaming
│       ├── net/        # TLS desync ClientHello record splitter & DoH
│       └── db/         # SQLite watch history & bookmarks persistence
└── .github/
    └── workflows/
        └── android-build.yml # Automated CI/CD compilation & release pipeline
```

---

## 🛠️ Building & Developing

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Rust toolchain (stable) with `cargo-ndk` and Android NDK (r26b+)

### Native Engine (Rust Core)
```bash
cd core
cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release
```

### Android APK (Kotlin Compose)
```bash
cd android
./gradlew assembleRelease
```

*Note: Automated cross-compilation and signing run on GitHub Actions for every tagged release (`refs/tags/v*`).*

---

## 📄 License

Miru is distributed under the terms of the **GNU General Public License v3.0 (GPL-3.0)**, preserving the copyleft licensing of the original project. See [LICENSE](LICENSE) for the full text.
