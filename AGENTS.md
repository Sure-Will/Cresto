# Cresto project instructions

## Scope and existing work

- The Android application is under `app/` and uses Kotlin and Compose. Inspect the affected implementation and current tests before changing behavior.
- Preserve pre-existing tracked and untracked work, saved user choices, and personal fork customizations. Do not reset, clean, overwrite configuration, or change remotes as a shortcut.
- Resolve repository identity, remote, branch, SDK and JDK from the current checkout. A stale README or remembered release is not evidence of the installed version.

## Build and validation

- Use the repository Gradle wrapper and a JDK compatible with the current Gradle/Android plugin. On this Mac, Android Studio's bundled JDK is an available candidate; verify it before setting `JAVA_HOME` for the command.
- Unit tests: `bash ./gradlew :app:testDebugUnitTest`. Focus an existing test with `--tests` when the change is local.
- AI request policies: `AiTodoExtractorRequestPolicyTest`. Image encoding boundaries: `ScreenshotDataUrlTest`. Real image compression coverage lives in `app/src/androidTest/`.
- The checked-out wrapper may lack an executable bit; use `bash ./gradlew` rather than changing source permissions.
- Debug build: `bash ./gradlew :app:assembleDebug`. Device instrumentation requires an authorized real device and an explicitly selected test scope.
- `assembleRelease` and `bundleRelease` finalize with `incrementVersionCode`, which writes `app/version.properties`. A verification-only release build must use an isolated copy or explicitly exclude that task; report the source and resulting artifact version accurately.
- Builds and tests create output files and caches. Do not run them as part of a read-only inspection.

## AI and UI acceptance

- A text-only connection test does not verify image recognition. Exercise the image request path, distinguish connection and response failures, and keep custom provider behavior covered.
- Never log credentials, full image data URLs, or private user image contents to diagnose a request.
- Preserve business callbacks and saved settings during UI changes. Use rendered-device evidence and the relevant interaction, not only a successful build.
- Distinguish source changes, built APK, installed APK, functional device verification and published release. Bind final acceptance evidence to the final artifact hash.
- Use existing session or project authorization for installation, pushing and releases. If a required choice remains uncertain, complete independent work and ask before that action.

## Continuation

- At an interruption, record the goal, initial dirty files, completed changes, exact validation evidence, remaining blocker and next action in the task's existing status record. Recheck live state before resuming.
