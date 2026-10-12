# Channel Finder TV Product Requirements

_Last updated: 2026-10-11 (UI/UX overhaul)_

## Product summary

Channel Finder TV is a Google TV / Android TV app for finding live channels in a large user-supplied Xtream Codes playlist. It is designed for a viewer who finds typical IPTV apps difficult to navigate. The playlist currently contains 24,057 live channels, so the product prioritizes direct destinations, local search, simple favorites, and a live preview while browsing.

The project is a working prototype. It builds a debug APK, runs on a Google TV emulator (Android 14), loads the full playlist, and plays live streams in both the muted preview and full screen. It has not yet been tested on a physical Google TV device (see [Known issues](#known-issues)). The UI follows the Android TV design guidelines (developer.android.com/design/ui/tv): 58 dp side / 28 dp top-bottom safe margins, standard card widths, a single focus treatment, no on-screen back buttons, and Back-to-top on Home.

## User and problem

The primary viewer is a family member using a TV remote, not a phone or mouse. He wants to find French and Portuguese channels quickly, with sports easy to reach. The playlist is much larger than can be browsed comfortably as one long list. There is no separate EPG source.

The interface must work with D-pad focus and OK/Back actions. It should make the next useful action obvious and avoid requiring the viewer to understand provider category IDs or Xtream terminology.

## Goals

- Make French, Portuguese, sports, channel favorites, recent channels, and pinned categories reachable directly from Home.
- Let viewers search the local channel catalog without waiting for another provider request.
- Let viewers pin a provider category from a searchable category picker and reopen it from Home.
- Show a muted live preview for the focused channel in the browser, then open that channel full-screen with OK.
- Keep browsing responsive and predictable with a playlist of roughly 24,000 channels.
- Store the account securely at rest and keep account details out of source code and documentation.

## Non-goals for the current release

- A program guide grid, recording, catch-up TV, parental controls, or channel editing.
- Providing or hosting any TV content. The app is a client for a user's own service account.
- A phone-first interface or touch-only interaction.
- Automatic metadata enrichment from third-party services.

## Core experience

### First run and account setup

1. The viewer enters the Xtream server address, username, and password. Focusing a field never opens the keyboard; OK starts editing, and the keyboard's Next action moves to the next field. An address without a scheme gets `http://`.
2. The app validates the account by loading the catalog, and only then saves it encrypted with AES/GCM using a key held by Android Keystore.
3. Home opens. The catalog is cached on device (`CatalogCache.kt`, metadata only), so later launches open Home from the cache and refresh silently in the background.
4. Connection, authentication, and unexpected-response failures are shown in plain language on the form, which keeps what the viewer typed. A failed load for a saved account shows **Réessayer** / **Modifier le compte**.
5. **Réglages** (gear on Home) offers: refresh the channel list, change the account (server shown, never the username), and clear recents.

Many Xtream providers only offer HTTP and redirect stream requests to other servers (often bare IP addresses), so the app permits cleartext traffic to any host. The setup screen warns that HTTP does not protect account details or streams in transit. Keep this warning visible; encrypted local storage does not encrypt network traffic.

### Navigation drawer

Browsing screens (Accueil, lanes, search, Catégories, Réglages) sit in a modal navigation drawer, per the Android TV navigation drawer guideline. Collapsed, it is an 80 dp icon rail; focusing it (Left from the leftmost element) expands it over the content with a scrim, on the current destination's item. Items: **Rechercher** at the top (searches every channel); **Accueil, France, Português, Sports, Événements, Favoris** as the six main destinations; **Catégories** and **Réglages** at the bottom. Récents has no item; its full list opens from the "Tout voir" card at the end of Reprendre. The active destination has a highlighted background. Événements shows a red dot (count when expanded) while matches are live. Playback, setup and loading screens have no drawer.

Back follows the guideline for side navigation: on a screen opened from the drawer, Back (after returning a scrolled list to the top) opens the drawer on that screen's item; Back in the drawer goes to Accueil; on Accueil, Back opens the drawer and a further Back leaves the app. Sub-pages (a category opened from the picker, Récents) return to the screen they came from. Each list's own search field searches only that list ("Rechercher dans France").

### Home

Home should answer “what can I watch quickly?” It is a vertical stack of rows: four lane cards (France, Português, Sports, Événements), **Reprendre** (recent channels with logos, OK plays directly, ending with "Tout voir"), **Mes favoris**, and **Catégories épinglées**. Search, Favoris, Catégories and Réglages are in the drawer. The France card has focus when Home opens; returning to Home restores the last focused card. Back from anywhere below the first card scrolls to the top and focuses France; Back from France leaves the app (no confirmation).

Pinned category shortcuts show their category label and channel count. Selecting a shortcut opens the category's channel list directly. Category favorites are stored locally by Xtream category ID, not by parsing the category label.

### Événements (event feeds)

Many provider channels only carry a picture during a match: event/PPV groups, numbered match feeds (CANAL+ LIVE 4, LIGUE1+ 3, LA LIGA 2…), and feeds renamed with the match and kickoff time ("DAZN FR 003| Rennes vs. AJ Auxerre | Ligue 1+ (2026-10-11 17:15:00)"). `ChannelText.isEvent` detects them from category/name wording only (no provider IDs). French and Portuguese-language events go to their own **Événements** lane (Home card + chip) and leave the France, Português, and Sports lanes; events for other audiences stay reachable from their category and search. The lane shows the match title, competition, and status (**EN COURS** for 3 h after kickoff, start time, "Pendant les matchs" for generic feeds, "Terminé"/"Aucun événement" last), and lists each match once even though the provider publishes most matches in a main and a backup group. Matches are recognised across naming styles ("Atalanta vs. Venise" and "SERIE A ENILIVE - ATALANTA VS. VENISE", or teams written after the competition), titled "Team vs. Team" in readable case, and counted once on Home. Before kickoff (more than 15 minutes ahead) no stream is opened: the preview and full screen say when it starts, with **Essayer quand même** in full screen. Event feeds never go to Récents/Reprendre, since they carry other programmes outside the match.

### Unavailable channels

Measured on 2026-10-11 with a debug probe (`StreamProbe.kt`): the account allows one connection (`max_connections=1`), yet nearly all failures are HTTP 407 answered by the provider per channel. Some channels always fail (all France 3 regional feeds of `FR ‖ FRANCE 3 • REGIONS`, Magnus TV, TVI FHD), others fail intermittently (RTP 1 alternated between OK and 407 with 15 s idle gaps). Results were identical with eight different User-Agents, and two concurrent streams usually worked, so the app is not the cause. The app therefore:

- retries a failed stream twice (1.5 s, then 3 s) before showing an error, in the preview and full screen;
- remembers failures for 12 hours (`AppState.unavailable`, persisted): such channels are dimmed, labelled **INDISPONIBLE**, sorted after working ones in lanes and search, and skipped when zapping; a later success clears the mark;
- orders lanes general-interest first (keywords such as GENERAL/TNT, then info, regional/local/backup last), so France no longer opens on France 3 regional feeds;
- explains a 407 as the provider refusing that channel and suggests another version of it.

### Category picker

The picker searches category labels locally (accent-insensitive). Rows are grouped under **Épinglées sur l’accueil**, France, Português, Sports, and Autres. OK opens a category; Right then OK (or a long press) pins it. Sections are recomputed when the list opens or the query changes, so pinning never moves the focused row. Returning from a category restores focus on it. Group names keep their quality words, so "PORTUGAL" and "PORTUGAL HEVC" stay distinct. When the provider publishes the same group twice under parallel prefixes (for example "┃FR┃ DISNEY+ LA LIGA" and "FR ‖ FR • DISNEY+ LA LIGA"), later copies are numbered in provider order: "DISNEY+ LA LIGA (2)". Same titles in different countries are told apart by their country badge instead.

The supplied playlist snapshot identified `731` as `PT ‖ DESPORTO PORTUGAL` and `1278` as `PT ‖ VODAFONE`. Treat these as snapshot examples only; load category labels and IDs from the provider instead of hardcoding them.

### Channel browser and preview

The browser uses two panes: a focusable channel list on the left and a 16:9 live preview with channel details and actions on the right. Moving focus to a channel updates the preview selection. The preview starts muted after 650 ms without a focus change, which avoids opening a new stream for every quick D-pad movement. The viewer can choose **Regarder** or press OK on a channel row to open full-screen playback. Favorites are toggled with a long press on OK (any channel row or card) or the preview's favorite button; rows have no second focus target. Right from the list always lands on **Regarder**; Up from the list lands on the selected lane chip; Left/Down return to the last focused row. Rows show the provider logo (Coil, bounded to 256 px, initials fallback), a cleaned name, country and quality badges, and a star for favorites. The previewed row stays highlighted while focus is in the preview pane.

A **Qualité** row under the lane chips filters the list by video quality (Toutes, 4K, Full HD, HD, SD), read from the channel name's quality tag or, failing that, the category's ("FRANCE • UHD/4K", "FRANCE • SD"). Channels without a quality tag only appear under Toutes. The choice is remembered between sessions and applies to every lane; an empty result offers **Toutes les qualités**.

Quality is not applied in Événements, Favoris and Récents (events rarely carry a quality tag; favorites and recents are the viewer's own picks), and the row is hidden there.

A **Pays** filter sits in the same row whenever the list on screen (lane or category) mixes at least two countries with two or more channels each, for example Brésil/Portugal in Português or the countries of `LT | LATINO`. Countries come from the channel's prefix, else its category's, mapped to ISO codes (IND→IN, USA→US…); provider prefixes that are regions or languages (EU, LA, AS, AF, AR…) are never shown as countries and such channels only appear under **Tous**. The country choice belongs to the current list and resets when the lane or category changes.

A country only gets a chip with at least 5% of the list (and two channels), so a handful of stray channels never overflows the row; long official names use short French forms (Centrafrique, États-Unis…). Rows show the group name only where it changes; the preview always shows it. Home lane counts follow the saved quality filter ("340 en Full HD").

Lanes list the viewer's own countries first (Português: Portugal then Brazil; Sports: France, Portugal, Brazil), then general-interest groups. Back in a scrolled list returns to the top first; a second Back leaves. In Récents the preview offers **Retirer** to drop one channel; clearing the whole history in Réglages needs a second press. Category screens show their channel count in the header and do not repeat the group name on each row. Favoris, Récents and Événements have no quality or country filter.

Search ranks matches (exact name, then name prefix, then word prefix, then anywhere), ignores accents, and requires every typed word to match. Channel names are cleaned for display only (`ChannelText.kt`): country prefixes such as `|FR|` become a badge and quality suffixes (HD, FHD, 4K, HEVC…) become badges. Raw names are still searched.

When returning from full-screen playback, restore the selected channel and list position. While the stream buffers, show a clear progress message. If preview playback fails, explain that the viewer can still try full-screen playback. An empty search result should remove the preview and provide a clear empty state. The D-pad must be able to move from the search field into the results.

### Full-screen playback

Media3/ExoPlayer plays the selected live stream with sound; there is no seek bar (live TV). A channel banner (logo, name, badges, position in list, clock, previous/next channel) shows on start and on any key, and hides after 5 s; OK toggles it. Up/Down (and CH+/CH−) zap through the list the channel was opened from; the banner follows immediately and the stream switches once the viewer pauses (500 ms). A failed stream is retried once silently; then a card explains the failure in plain language (HTTP 407, 404, connection limits, unsupported format…) with **Réessayer** and **Chaîne suivante**. Back returns to the previous screen (an in-app back stack) with the last watched channel selected.

## Functional requirements

| ID | Requirement | Current status |
| --- | --- | --- |
| FR-1 | Load live categories and live streams through the Xtream player API. | Verified on emulator: 24,057 channels load. |
| FR-2 | Keep channel ID, category ID, category label, logo URL, EPG ID, and derived lane with each channel. | Implemented. |
| FR-3 | Search channel names, category labels, and lane labels locally. | Verified: "TF1" returns 36 ranked results, exact match first; D-pad leaves the field. |
| FR-4 | Classify French, Portuguese, and sports channels into direct Home lanes. | Category country code wins over name words (2026-10-11): France 1,870, Português 769 (now includes `BR |` groups), Sports 391 (French/Portuguese only since 2026-10-11), Événements 423 after de-duplication. |
| FR-5 | Pin/unpin categories and open them directly from Home. | Verified on emulator: pin from picker, card appears on Home. |
| FR-6 | Pin/unpin channels and retain a recent-channel list. | Verified on emulator: long-press favorite, Home row, recents ordered by recency. |
| FR-7 | Preview the selected live channel while browsing, muted by default and delayed until focus settles. | Verified on emulator: plays muted; failure state shown. |
| FR-8 | Open a channel full-screen and restore the prior browsing context on Back. | Verified on emulator: plays with sound; zapping works; Back restores the last watched row. |
| FR-9 | Show an EPG marker only when a channel has an EPG ID. | Verified ("GUIDE" marker shown). No guide data is available. |

## Technical design

- **Platform:** Native Kotlin Android app using Jetpack Compose for TV and Android TV launcher metadata.
- **Toolchain:** JDK 17, Gradle 9.8.1, Android Gradle plugin 9.4.1 (built-in Kotlin), Kotlin Compose compiler plugin 2.4.21, compileSdk 37, targetSdk 35, minSdk 23, Compose BOM 2026.09.00, Media3 1.11.1.
- **Navigation and screen state:** `Page` and `BrowseLane` in `Channel.kt`; the current screen flow is managed in `MainActivity.kt`.
- **Provider access:** `XtreamRepository.kt` calls `player_api.php` actions `get_live_categories` and `get_live_streams`. It maps `category_id` to `category_name`, and maps each live stream's `stream_id`, `name`, `category_id`, `stream_icon`, and `epg_channel_id` into a `Channel`.
- **Stream URL:** Live playback is built as `/live/{username}/{password}/{stream_id}.ts` by `XtreamRepository.streamUrl`. The provider redirects these requests to separate stream servers.
- **Playback:** `buildStreamPlayer` in `MainActivity.kt` creates the ExoPlayer used for both browser preview and full-screen playback. It uses a `DefaultHttpDataSource` with a VLC User-Agent, cross-protocol redirects, and explicit timeouts. The browser preview has its own player, is muted, and is released when the browser leaves composition.
- **Persistence:** `CredentialStore.kt` encrypts account data with AES/GCM and Android Keystore. Channel favorites, category favorites, and recent IDs use local SharedPreferences.
- **Performance:** The catalog and ID lookup map are held in memory (about 135 MB PSS on the emulator with the full catalog). Channel results render in a LazyColumn; search and lane filtering are local. Counts are grouped for Home and the category picker.
- **Network policy:** `network_security_config.xml` permits cleartext traffic to all hosts, because Xtream stream servers are not known in advance. Credentials are present in Xtream request and playback URLs, so avoid logging them or sharing live-session logs.

## Product quality and acceptance checks

Before calling a build ready, verify on a Google TV device or emulator with a representative large playlist:

1. The app installs, launches from the TV home screen, and remains usable with only the remote.
2. Home reaches each lane and pinned category without typing or scrolling through the full playlist.
3. Category search finds known French and Portuguese categories; stars pin and unpin them; pinned categories persist after app restart.
4. Channel search updates locally; an empty result is understandable and does not leave stale video playing.
5. D-pad focus changes the preview only after it settles; rapid movement does not start a stream per row; preview has no audio.
6. OK starts full-screen playback. Back restores the same selected row and scroll position. Favorites and Recents remain correct.
7. Preview buffering, unavailable streams, invalid credentials, network timeouts, and unsupported stream formats produce useful UI states instead of a crash.
8. The app remains responsive while searching and scrolling through the full playlist. Measure on the target device rather than assuming emulator performance represents the TV.

### Emulator results (2026-10-11, Google TV 1080p, Android 14, debug build)

| Check | Result |
| --- | --- |
| 1 | Installs with a TV banner and launches from the TV launcher. Every screen verified with the D-pad only. |
| 2 | All lanes and shortcuts visible on the first Home screen; pinned categories reachable from Home. |
| 3 | Category search and pinning verified; pins persist across restarts. |
| 4 | Search updates locally; empty result removes the preview and offers **Chercher partout** / **Effacer la recherche**. |
| 5 | Preview plays muted after focus settles. Rapid-movement behavior not yet measured on device. |
| 6 | Full screen plays with sound; zapping with Up/Down; Back restores the last watched row. Favorites and recents verified. |
| 7 | Preview and full-screen failures show plain-language messages; full screen offers retry / next channel. Invalid credentials and timeouts map to plain-language setup errors (not yet exercised against a real failure). |
| 8 | Cold start from cache ≈ 4 s on the emulator with a debug build (≈ 3.3 s reading the cache); a release build on hardware should be faster. Not yet measured on a target device. |

## Known issues

- **Lane classification:** the category's country code now decides the lane when present; name keywords are only used for untagged categories. Sports keeps only FR/PT/BR-tagged groups (391 channels); other countries' sports (including `BE ‖ BELGIQUE • SPORTS`) stay reachable from their category and search.
- **Provider-side dead channels:** many France/Português channels answer HTTP 407 (see [Unavailable channels](#unavailable-channels)). The app learns them as the viewer meets them; there is no background scan, to avoid extra provider connections on a one-connection account.
- **Event times:** kickoff times in feed names are read in the TV's time zone; the provider's zone is not documented. Events are considered live for 3 hours after kickoff.
- **Partial catalogs from the provider:** on 2026-10-12 a background refresh received 70 channels instead of ~24,000 and replaced the saved catalog. A refresh that returns less than half of a catalog of 1,000+ channels is now ignored (logged as `Ignoring partial catalog`).
- **Catalog cache read time:** reading ~24k channels from the binary cache takes about 3 s on the emulator with a debug build. Measure on the target TV with a release build before optimizing further.
- **Logos depend on the provider:** many `stream_icon` URLs are empty or fail; rows fall back to initials.
- **Display-name cleanup is heuristic:** only known country-code prefixes and quality tokens are stripped. Raw names are still searched.
- **Preview may use an additional provider connection.** Verify provider connection limits on-device.
- **HTTP exposes traffic in transit.** Credentials are encrypted at rest, but the provider endpoint does not protect API or stream requests over the network.
- **No EPG source is configured.** The "GUIDE" badge only marks channels with an EPG ID. Do not build a guide screen until the user supplies a valid EPG source or chooses a metadata strategy.
- **Category and channel favorites are device-local.** They do not sync between devices or provider accounts. Recents are cleared when the account changes.

## Next steps

1. Install on a physical Google TV device: measure cold start, memory, scrolling, and preview connection limits with a release build.
2. Review lane classification against the full playlist and record false positives/negatives before changing rules; order lanes so major channels come first.
3. Exercise the failure paths against real failures: wrong password, unreachable server, timeout.
4. Decide whether the catalog cache needs a faster format (for example a database) after measuring on hardware.

## Key files

| File | Purpose |
| --- | --- |
| `app/src/main/java/fr/channelfinder/tv/MainActivity.kt` | Activity, theme, root back handling, screen routing. |
| `AppState.kt` | Back stack, catalog loading/refresh, favorites, pins, recents, per-screen focus/scroll state. |
| `Theme.kt` / `Components.kt` | Design tokens (palette, spacing, type) and shared TV components: focus surfaces, buttons, chips, logos, text field, hints, notices. |
| `HomeScreen.kt`, `BrowserScreen.kt`, `CategoryScreen.kt`, `PlayerScreen.kt`, `StartupScreens.kt` | Screens (setup, loading, error and settings live in `StartupScreens.kt`). |
| `Playback.kt` | `buildStreamPlayer`, the non-focusable video surface, and plain-language playback errors. |
| `ChannelText.kt` | Display-name cleanup, badges, lane classification, event detection/parsing, lane ordering, accent-insensitive ranked search. |
| `StreamProbe.kt` | Debug-build stream diagnostics (logs channel names and status codes only). |
| `CatalogCache.kt` | Binary on-device catalog cache (metadata only, account identified by hash). |
| `XtreamRepository.kt` | Xtream category/channel requests, load steps, error translation, live stream URL creation. |
| `Channel.kt` | Account, category, channel, lane, and page models. |
| `CredentialStore.kt` | Encrypted account persistence using Android Keystore. |
| `app/src/main/AndroidManifest.xml` | TV app, launcher banner and icon. |
| `app/src/main/res/mipmap-*/banner.png` | 16:9 TV launcher banner (with app name) and icon at every density. |
| `app/src/main/res/xml/network_security_config.xml` | Cleartext policy (all hosts). |
| `app/build.gradle.kts` | Android, Compose for TV, Media3, Coil, and Material icons dependencies. |
