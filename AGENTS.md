# AGENTS.md

Guidance for coding agents working on Channel Finder TV, a Kotlin / Jetpack Compose for TV Xtream IPTV player for Google TV. Read `PRD.md` first: it holds the product requirements, current status, and known issues.

## Hard rules

- **Never log, print, commit, or share Xtream credentials or stream URLs.** Stream and API URLs embed the username and password (`/live/{user}/{pass}/{id}.ts`, `player_api.php?username=…&password=…`). Redact URLs and IPs when reading logcat, for example: `adb logcat -d | sed -E 's#[a-z]+://[^ )"]+#<URL>#g'`.
- Do not hardcode provider hosts, usernames, or passwords in source, docs, or tests. Credentials are entered on the TV and stored encrypted by `CredentialStore.kt`.
- Do not type real account credentials into the app yourself; ask the user to enter them.
- Do not hardcode provider category IDs or labels; load them from the API.
- Keep the setup screen's HTTP warning visible.

## Toolchain

| Component | Version |
| --- | --- |
| JDK | 17 (Homebrew `openjdk@17`) |
| Gradle wrapper | 9.8.1 |
| Android Gradle plugin | 9.4.1 (built-in Kotlin; do not re-add `org.jetbrains.kotlin.android`) |
| Kotlin Compose compiler plugin | 2.4.21 |
| compileSdk / targetSdk / minSdk | 37 / 35 / 23 |
| Compose BOM | 2026.09.00 (Compose 1.12 requires compileSdk 37 and AGP 9.1+) |
| Media3 | 1.11.1 |

Kotlin JVM target is set with `kotlin { compilerOptions { … } }`; `kotlinOptions` no longer exists under AGP 9.

## Build

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. There is no `local.properties`; `ANDROID_HOME` supplies the SDK path.

## Run on the emulator

An Android TV image is required (`system-images;android-34;google-tv;arm64-v8a`); the AVD is `Google_TV_1080p_API_34`.

```bash
$ANDROID_HOME/emulator/emulator -avd Google_TV_1080p_API_34 -no-snapshot-save &
$ANDROID_HOME/platform-tools/adb wait-for-device
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/platform-tools/adb shell monkey -p fr.channelfinder.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```

Drive the UI with the D-pad (`adb shell input keyevent DPAD_DOWN|DPAD_UP|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|BACK`) and capture with `adb exec-out screencap -p > shot.png`. Test with the remote only: anything that needs a tap is a bug. `adb install -r` keeps the saved account, so re-entering credentials is not needed between builds.

## Project layout

- `MainActivity.kt`: activity, theme, root Back handling, screen routing.
- `AppState.kt`: back stack (`Page`), catalog load/refresh, favorites, pins, recents, saved focus and scroll positions.
- `Theme.kt`, `Components.kt`: design tokens and shared TV components. Use `FocusSurface` (light fill on focus) for rows/buttons/chips, `CardSurface` (white outline) for artwork cards, and `TvTextField` for any text input (it never opens the keyboard on focus).
- `HomeScreen.kt`, `BrowserScreen.kt`, `CategoryScreen.kt`, `PlayerScreen.kt`, `StartupScreens.kt` (setup, loading, error, settings).
- `Playback.kt`: `buildStreamPlayer`, video surface, plain-language playback errors.
- `ChannelText.kt`: display-name cleanup, badges, lane classification, event detection and parsing, lane ordering, ranked accent-insensitive search. `CatalogCache` stores lanes: bump its `VERSION` whenever lane rules change.
- `StreamProbe.kt`: debug builds only. `adb shell am start -S -n fr.channelfinder.tv/.MainActivity -e probe 30` checks streams in the France and Português lanes one at a time; read `adb logcat -s StreamProbe` (names and status codes only, never URLs).
- `CatalogCache.kt`: binary on-device catalog cache (metadata only).
- `XtreamRepository.kt`: Xtream API calls, error translation, stream URL building.
- `Channel.kt`: models, `Page`, `BrowseLane`.
- `CredentialStore.kt`: AES/GCM + Android Keystore account storage.
- `res/xml/network_security_config.xml`: cleartext allowed for all hosts, because providers redirect streams to unknown servers.

## Working conventions

- UI text is French; keep new strings in French and consistent with existing wording.
- Design for a TV at sofa distance and follow the Android TV design guidelines: D-pad focus must always be visible, reachable, and predictable; text must be high-contrast and at least 14 sp; keep the 58 dp side / 28 dp top-bottom margins; no on-screen back buttons; secondary actions use a long press on OK.
- Never show the Xtream username or password on screen outside the account form.
- The Google TV emulator sometimes shows a "Set up Google TV" prompt over the app; relaunch with `adb shell am start -n fr.channelfinder.tv/.MainActivity`.
- Preview stays muted and starts only after focus settles (650 ms). Release players when leaving composition.
- Many channels fail on the provider side with HTTP 407, some always and some intermittently (see PRD "Unavailable channels"). Verify playback with a known-good channel (search "TF1") or the stream probe before blaming the app.
- When driving the emulator, never press Back on Home with France focused: it leaves the app, and the Google TV setup/sign-in screens take the next keystrokes. Check `dumpsys activity activities | grep topResumed` before typing.
- After any change, build, reinstall on the emulator, and check the affected screen with screenshots before reporting it done.
- Update the status tables and known issues in `PRD.md` when you verify or fix something.
