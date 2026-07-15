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
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` - Full CI verification (run before push)

Use `./gradlew` on macOS/Linux. JDK 17 required (AGP 9.x). Open the repository root in Android Studio for Compose previews and interactive debugging.

## Release Builds

`build-release.ps1` builds and signs a release APK. It requires:

- A keystore at `%USERPROFILE%\pocket300-release.jks` (override with `-KeystorePath`)
- `ANDROID_SDK_ROOT` or `sdk.dir` in `local.properties`

Run: `.\build-release.ps1` - prompts for keystore password and outputs to `app\build\outputs\apk\release\app-release-signed.apk`.

CI releases are triggered by tags matching `v*` (e.g., `v1.2.3`, `v1.0.0-beta.1`). Pushing such a tag builds and publishes a signed release APK to GitHub Releases.

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

## Animation Guidelines

- Navigation to the same content type must use the same animation across every entry point.
- Every thread card that opens `ThreadScreen` must participate in the shared-bounds transition defined by `SharedTransitionLayout`. Pass the `SharedTransitionScope` and `AnimatedVisibilityScope` from `Pocket300App.kt` to screens that contain thread cards.
- Use `rememberSharedContentState("thread-$threadId")` on both the source thread card and `ThreadScreen`; do not create screen-specific keys, durations, or transition styles for individual thread-card entry points.
- When adding or changing a thread-card entry point, verify its forward and back navigation against the forum, search, favorites, reading-history, and custom-list cards so their opening and closing motion remains consistent.

## Commit and Push Workflow

- Write commit messages in English using Conventional Commit format with a scope: `feat(api): ...`, `feat(ui): ...`, `fix(api): ...`, `refactor(ui): ...`, `test: ...`, `chore: ...`.
- Keep each commit focused on one logical change. Do not include unrelated working-tree changes, generated build output, local configuration, credentials, or signing material.
- Run `testDebugUnitTest lintDebug assembleDebug` before pushing. If verification cannot run or fails for an unrelated reason, report it explicitly in the pull request.
- Do not commit or push feature work directly to `main`. Start a descriptively named branch from the latest `origin/main` (for example, `feat/...` or `fix/...`).
- After completing a requested change, commit it, push the branch, open a pull request targeting `main`, and merge it after required CI checks pass unless the user explicitly asks to keep the work local or leave the PR unmerged.
- Use English for pull request titles and descriptions. After merging, update the local `main` branch with a fast-forward pull and confirm the working tree is clean.
