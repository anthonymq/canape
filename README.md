# Channel Finder TV

A native Android TV / Google TV starter app built around quick channel discovery rather than a full-screen EPG.

## First-run experience

1. Enter the Xtream server address, username, and password on the TV.
2. The app loads live categories and channel metadata from the Xtream API.
3. Home provides direct destinations for **France**, **Português**, **Sports**, **Favorites**, **Recent**, and the category picker.
4. Search filters the local channel catalog immediately. The channel browser uses a two-pane layout: focused channel rows on the left and a muted live preview on the right. The preview waits briefly after focus settles, so fast D-pad browsing does not repeatedly start streams. OK opens full-screen playback; Back restores the selected channel.

## Category favorites

Open **Catégories** from Home, search or browse the provider's category list, then select the star on a category row. Pinned categories appear on Home with their channel counts and open directly to that category. Category favorites are stored locally by Xtream category ID, so labels do not need to be parsed from channel names.

The app does not include a program guide. It shows a small guide marker only for channels that provide an EPG ID.

## Security notes

- No provider credentials are embedded in the project.
- Credentials entered on the TV are encrypted with an AES/GCM key held by Android Keystore.
- Many providers use HTTP and redirect streams to other hosts, so the app allows cleartext traffic to any host (`app/src/main/res/xml/network_security_config.xml`). The setup screen warns that HTTP does not encrypt credentials or streams in transit.
- The provider's Xtream API includes credentials in request URLs. Avoid sharing logs or screenshots from a live session.

## Open and run

See `AGENTS.md` for the toolchain (JDK 17, AGP 9.4.1, compileSdk 37), build, and emulator commands. Test playback on the target Google TV device; provider stream formats and network behavior vary.

## Current scope

- Xtream live categories and live channel metadata
- Fast home shortcuts, pinned categories, and in-memory local search
- Muted, focus-driven live preview with a short switch delay
- Local favorites and recent channels
- Media3 TS playback
- No EPG grid, recording, parental controls, or duplicate deletion
