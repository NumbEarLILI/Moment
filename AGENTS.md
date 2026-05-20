# Agent environment (Cursor Cloud)

## Android SDK

Cloud agents use `.cursor/environment.json`, which runs `scripts/setup-android-sdk.sh` on environment install. The SDK is installed under `.android-sdk/` (gitignored).

Required packages: `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`.

Environment variables:

- `ANDROID_SDK_ROOT` / `ANDROID_HOME` → `/workspace/.android-sdk`

Gradle resolves the SDK without hand-editing `local.properties`: `settings.gradle.kts` writes `sdk.dir` from those variables (or from `.android-sdk` if present). The setup script also writes `sdk.dir` when the file is missing.

## Build and test

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Optional map API keys: set `AMAP_*` env vars or entries in `local.properties` (see README). Builds succeed without them; map features need keys.
