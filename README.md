# Tatap (Android Native Kotlin + Rust Core)

> High-performance anime streaming application for Android (Mobile & TV), featuring a native Kotlin Jetpack Compose UI (Crunchyroll-style aesthetic) and an embedded Rust daemon core.

## Architecture

- **UI Layer (`android/`):** 100% Native Kotlin with Jetpack Compose, Material 3, and AndroidX Media3 ExoPlayer.
- **Engine Core (`core/`):** Embedded Rust daemon (`libtatap_core.so`) compiled via `cargo-ndk`, serving video proxy, stream resolver, TLS desync anti-DPI, and SQLite caching on `127.0.0.1:8767`.
- **CI/CD (`.github/workflows/`):** Cloud-native APK compilation on GitHub Actions runners to conserve local developer disk space.

## Directory Structure

```
tatap/
├── android/            # Android Gradle project (Kotlin, Jetpack Compose, Media3)
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/id/tatap/app/  # Kotlin source code
│   │       ├── jniLibs/            # Native .so libraries from Rust
│   │       └── res/                # Drawables, layouts, values
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── core/               # Rust embedded daemon (Axum, Tokio, Scrapers, Proxy)
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs      # JNI bridge & daemon entrypoints
│       ├── api/        # Stream resolvers & scrapers
│       ├── proxy/      # M3U8 rewrite & TS segment streaming
│       └── desync/     # 1-byte ClientHello TLS desync anti-DPI
└── .github/
    └── workflows/
        └── android-build.yml # Automated cloud APK build workflow
```
