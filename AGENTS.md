# AGENTS.md

Guidance for coding agents working on Canapé, a Kotlin / Jetpack Compose for TV Xtream IPTV player for Google TV. Read `PRD.md` first: it holds the product requirements, current status, and known issues.

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

An Android TV image is required (`system-images;android-34;google-tv;arm64-v8a`); the existing AVD is `Google_TV_1080p_API_34`. Run these commands from this repository directory. Set the SDK path even if you have not run the build commands in this terminal.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/emulator/emulator" -list-avds
"$ANDROID_HOME/platform-tools/adb" devices
```

Reuse a running TV emulator instead of starting a second copy. If none is running, start the AVD in the background; its startup log goes to the temporary directory:

```bash
nohup "$ANDROID_HOME/emulator/emulator" -avd Google_TV_1080p_API_34 -no-snapshot-save > "${TMPDIR:-/tmp}/easy-iptv-emulator.log" 2>&1 &
```

Wait for ADB, then for Android to finish booting (`wait-for-device` alone does not guarantee boot readiness). These commands select the emulator; if multiple emulators are running, replace `-e` with `-s` and the serial shown by `adb devices`.

```bash
"$ANDROID_HOME/platform-tools/adb" -e wait-for-device
until [ "$("$ANDROID_HOME/platform-tools/adb" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done
```

Build the debug APK using the Build section above if needed, then install and open the app:

```bash
"$ANDROID_HOME/platform-tools/adb" -e install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -e shell am start -n fr.channelfinder.tv/.MainActivity
```

In a restricted Codex sandbox, ADB may fail with `could not install *smartsocket* listener: Operation not permitted`, and the emulator may report a Crashpad permission error. Retry emulator/ADB commands with the tool's elevated execution permission rather than changing the AVD or reinstalling the SDK. Wait for approval before retrying if the tool requires it.

Drive the UI with the D-pad (`adb shell input keyevent DPAD_DOWN|DPAD_UP|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|BACK`) and capture with `adb exec-out screencap -p > shot.png`. Test with the remote only: anything that needs a tap is a bug. `adb install -r` keeps the saved account, so re-entering credentials is not needed between builds.

## Project layout

- `MainActivity.kt`: activity, theme, root Back handling, screen routing.
- `AppState.kt`: back stack (`Page`), catalog load/refresh, favorites, pins, recents, saved focus and scroll positions.
- `Theme.kt`, `Components.kt`: design tokens and shared TV components. Use `FocusSurface` (light fill on focus) for rows/buttons/chips, `CardSurface` (white outline) for artwork cards, and `TvTextField` for any text input (it never opens the keyboard on focus).
- `NavDrawer.kt`: modal navigation drawer around browsing screens (`AppDrawer`), drawer Back handling, `DrawerContentInset`. Destinations are `NavDest`; `AppState.openDestination` resets the stack to Home + destination, `activeDest`/`isDrawerRoot` decide the highlighted item and what Back does, `focusDrawer()` moves focus into it.
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

- Stop when the requested task is complete and its relevant checks have passed or any verification limits have been reported. Deliver a concise final response, then stop; do not continue with extra checks, cleanup, refactors, or unrelated improvements. Documentation-only tasks require documentation checks, not an app rebuild or emulator run.
- UI text is French; keep new strings in French and consistent with existing wording.
- Design for a TV at sofa distance and follow the Android TV design guidelines: D-pad focus must always be visible, reachable, and predictable; text must be high-contrast, at least 14 sp for running text and 12 sp for badges/eyebrows (the user asked for a denser UI on 2026-10-11, so keep rows around 56 dp rather than growing them back); keep the 58 dp side / 28 dp top-bottom margins; no on-screen back buttons; secondary actions use a long press on OK.
- `FocusSurface`/`CardSurface` fall back to a foundation-`clickable` surface when there is no long press, because TV Material's `Surface` always reports a long-press action to accessibility services. Only pass `onLongClick` when the element really has one.
- Text fields: one Back closes the keyboard and ends editing (`TvTextField` watches the IME insets). In the browser the search field is three Ups from the list (list → quality/country row → lane chips → search).
- Never show the Xtream username or password on screen outside the account form.
- The Google TV emulator sometimes shows a "Set up Google TV" prompt over the app; relaunch with `adb shell am start -n fr.channelfinder.tv/.MainActivity`.
- Preview audio is enabled by explicit user request (2026-10-18), after focus settles (650 ms). The app-owned LivePlaybackSession survives preview/fullscreen handoff; never release it from either surface. Stop on settled empty browsing results and background; release when leaving live browsing/playback or the app composition.
- Many channels fail on the provider side with HTTP 407, some always and some intermittently (see PRD "Unavailable channels"). Verify playback with a known-good channel (search "TF1") or the stream probe before blaming the app.
- With the drawer, Back on Accueil opens the drawer and the next Back leaves the app. The first D-pad key in the second after launch may only give focus to the first card.
- When driving the emulator, never press Back on Home with France focused: it leaves the app, and the Google TV setup/sign-in screens take the next keystrokes. Check `dumpsys activity activities | grep topResumed` before typing.
- After any change, build, reinstall on the emulator, and check the affected screen with screenshots before reporting it done.
- Update the status tables and known issues in `PRD.md` when you verify or fix something.
