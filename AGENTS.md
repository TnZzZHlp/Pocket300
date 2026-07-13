# Repository Guidelines

## Project Structure

Single-module Android app built with Kotlin and Jetpack Compose. Production code is under `app/src/main/java/com/yamibo/pocket300/`:

- `api/` - Yamibo client, transport (`YamiboClient.kt`), and response parsing
- `data/` - Local SQLite storage (reading history)
- `ui/` - Compose screens and theming; `Pocket300App.kt` is the main UI entry
- `MainActivity.kt` - Application entry point

Local JVM tests mirror the production package tree under `app/src/test/`. Device/emulator tests belong in `app/src/androidTest/`. Dependency versions are in `gradle/libs.versions.toml`.

## Build Commands

Run from repository root with the Gradle wrapper:

- `.\gradlew.bat assembleDebug` - Build debug APK
- `.\gradlew.bat testDebugUnitTest` - Run local JUnit tests
- `.\gradlew.bat lintDebug` - Run Android lint
- `.\gradlew.bat installDebug` - Install debug build on connected device

Use `./gradlew` on macOS/Linux. Open the repository root in Android Studio for Compose previews and interactive debugging.

## Release Builds

`build-release.ps1` builds and signs a release APK. It requires:

- A keystore at `%USERPROFILE%\pocket300-release.jks` (override with `-KeystorePath`)
- `ANDROID_SDK_ROOT` or `sdk.dir` in `local.properties`

Run: `.\build-release.ps1` - prompts for keystore password and outputs to `app\build\outputs\apk\release\app-release-signed.apk`.

## Coding Conventions

- Package names are lowercase under `com.yamibo.pocket300`
- API files are named by domain: `YamiboPostsApi.kt`, `YamiboThreadsApi.kt`
- Test classes match the source: `YamiboPostsApiTest.kt` tests `YamiboPostsApi.kt`
- User-facing strings belong in `res/values/strings.xml`, not hardcoded

## Testing Notes

- Local tests use JUnit 4 with inline JSON fixtures as string literals (no external fixture files)
- Test method names describe behavior: `rejectsCommentAssignedToDifferentPost`, `fallsBackToPositionForInvalidDisplayNumber`
- New behavior and bug fixes should include focused regression tests

## Architecture Notes

- `YamiboClient` uses `AndroidCookieJar` to bridge OkHttp to Android's persistent cookie store - Discuz authentication is held in HttpOnly cookies and survives app restarts
- `Pocket300App.kt` is a large single file containing the main Compose UI; navigation and screen state are managed within it

## Commit Style

Conventional Commit format with scope: `feat(api): ...`, `feat(ui): ...`, `fix(api): ...`, `refactor(ui): ...`, `test: ...`, `chore: ...`.
