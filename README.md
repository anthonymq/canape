# Easy IPTV

A native Android TV / Google TV starter app built around quick channel discovery rather than a full-screen EPG.

## First-run experience

1. Enter the Xtream server address, username, and password on the TV.
2. The app loads live categories and channel metadata from the Xtream API.
3. Home provides direct destinations for **France**, **Português**, **Sports**, **Favorites**, **Recent**, and the category picker.
4. Search filters the local channel catalog immediately. The channel browser uses a two-pane layout: focused channel rows on the left and a muted live preview on the right. The preview waits briefly after focus settles, so fast D-pad browsing does not repeatedly start streams. OK opens full-screen playback; Back restores the selected channel.

## Category favorites

Open **Catégories** from Home, search or browse the provider's category list, then select the star on a category row. Pinned categories appear on Home with their channel counts and open directly to that category. Category favorites are stored locally by Xtream category ID, so labels do not need to be parsed from channel names.

The TV guide loads programme schedules from the saved Xtream provider, with times and programme details. Coverage depends on the provider; channels without schedules remain playable.

## Security notes

- No provider credentials are embedded in the project.
- Credentials entered on the TV are encrypted with an AES/GCM key held by Android Keystore.
- Many providers use HTTP and redirect streams to other hosts, so the app allows cleartext traffic to any host (`app/src/main/res/xml/network_security_config.xml`). The setup screen warns that HTTP does not encrypt credentials or streams in transit.
- The provider's Xtream API includes credentials in request URLs. Avoid sharing logs or screenshots from a live session.

## Open and run

See `AGENTS.md` for the toolchain (JDK 17, AGP 9.4.1, compileSdk 37), build, and emulator commands. Test playback on the target Google TV device; provider stream formats and network behavior vary.

## Download a Google TV APK

The [Google TV APK workflow](https://github.com/anthonymq/easy-iptv/actions/workflows/google-tv-apk.yml) runs on pushes and pull requests to `main`, version tags (`v*`), and **Run workflow**. It runs unit tests and builds the app using JDK 17 and Android SDK 37.

Pushing a version tag automatically publishes a GitHub prerelease with the tested APK attached. For example, after updating the app version and committing it, run `git tag v0.1.1` and `git push origin v0.1.1`. Release downloads are available on the [Releases page](https://github.com/anthonymq/easy-iptv/releases) without extracting a CI artifact ZIP. Rerunning a tag build replaces that release's APK. Main-branch and pull-request builds produce CI artifacts only. Only the tag release job receives write permissions; no additional token or signing secret is required.

Open a successful run and download the **easy-iptv-google-tv** artifact. Unzip it to get `easy-iptv-google-tv.apk`, then sideload it on an Android TV or Google TV device (Android 6.0 or newer):

```bash
adb install -r easy-iptv-google-tv.apk
```

This is a debug-signed APK for testing, not a Play Store release. CI artifacts expire after 30 days. GitHub runners generate their own debug signing key, so replacing an APK from a different build may require uninstalling the old app first, which removes its saved account and preferences. The application ID remains `fr.channelfinder.tv` to preserve existing local installations.

## Current scope

- Xtream live categories and live channel metadata
- Fast home shortcuts, pinned categories, and in-memory local search
- Muted, focus-driven live preview with a short switch delay
- Local favorites and recent channels
- Media3 TS playback
- Provider programme schedules and details
- No EPG grid, recording, parental controls, or duplicate deletion
