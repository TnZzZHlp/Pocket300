# Repository Guidelines

## Project Structure & Module Organization

Pocket300 is a single-module Android application built with Kotlin and Jetpack Compose. Production code lives under `app/src/main/java/com/yamibo/pocket300/`: `api/` contains the Yamibo client, transport, and response parsing; `ui/` contains Compose screens and theming; and `MainActivity.kt` is the application entry point. Android resources are in `app/src/main/res/`, with application metadata in `app/src/main/AndroidManifest.xml`. Local JVM tests mirror the production package tree under `app/src/test/`; device and emulator tests belong in `app/src/androidTest/`. Central dependency versions are maintained in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands

Run commands from the repository root with the checked-in Gradle wrapper:

- `.\gradlew.bat assembleDebug` builds a debug APK.
- `.\gradlew.bat testDebugUnitTest` runs local JUnit tests.
- `.\gradlew.bat connectedDebugAndroidTest` runs instrumentation tests on a connected device or emulator.
- `.\gradlew.bat lintDebug` performs Android lint checks.
- `.\gradlew.bat installDebug` installs the debug build on a connected target.

Use `./gradlew` instead on macOS or Linux. Open the repository root in Android Studio for Compose previews and interactive debugging.

## Coding Style & Naming Conventions

Follow standard Kotlin style with four-space indentation, trailing commas in multiline declarations, and idiomatic null-safety. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` for constants. Keep package names lowercase under `com.yamibo.pocket300`. Name API files by domain, such as `YamiboPostsApi.kt`, and keep related parsing tests in a matching `YamiboPostsApiTest.kt`. Prefer small composables and immutable UI state. Do not embed user-facing text when it belongs in `res/values/strings.xml`.

## Testing Guidelines

Local tests use JUnit 4 and should be fast and deterministic. Add parsing and validation coverage beside the corresponding API test class; use descriptive behavior names such as `rejectsCommentAssignedToDifferentPost`. Put Android framework or UI behavior in `androidTest` using AndroidX JUnit and Espresso. There is no stated coverage threshold, but new behavior and bug fixes should include focused regression tests.

## Commit & Pull Request Guidelines

Recent history favors Conventional Commit-style subjects such as `feat(api): ...` and `feat(ui): ...`; use an imperative, concise subject with a relevant scope. Keep commits focused. Pull requests should explain the change and verification performed, link relevant issues, and include screenshots or recordings for visible Compose UI changes. Ensure unit tests and lint pass before requesting review.
