# Canapé Product Requirements

_Last updated: 2026-10-20 (Stable v0.3.0 release preparation)_

Stable v0.3.0 (versionCode5): promotes the Salon Test1–15 application source to the normal `fr.channelfinder.tv` package, with the existing Canapé launcher identity and stable update channel. Includes compact navigation/fullscreen layout, audible shared live preview, remembered filters, simplified Home, persistent Films/Séries metadata and in-app updates. Test15 sidebar correction is included. Signing/publication uses the existing GitHub version-tag workflow and distribution certificate. Native Test15 evidence covers unchanged app behavior; stable-package build/signature/update metadata and public artifact verification are release-specific checks. Historical test-package distribution entries below do not describe the stable APK.

Salon Test15: sidebar starts at Accueil and removes its search entry; live/library search remains in content toolbars. Rail focus is allowed after an explicit Left/Back request or while already navigating the drawer, rather than after any previous content focus. Per-page permission prevents the Home→France transient expansion during list calculation. Global-search pages use Home as the drawer focus fallback. Native before/after recordings reproduce Test14's flash and show collapsed rail throughout two normal and one130% Test15 transitions; deliberate drawer opening/return, sibling roots/details/episodes and scoped/global search remain reachable. Build/install,61tests and lint pass (0errors,25existingwarnings,3hints). Fresh27 finalPNGs plus2baselinePNGs and limits: `.omo/evidence/sidebar-focus/QA.md`. PhysicalGoogleTV/playback are not recertified.

Main integration (2026-10-20): Salon Test1–14 application changes, update publishing tools and documentation are integrated together. The standard stable-package debug build (without Salon init-script overrides), all61 unit tests and lint pass; the existing native Test14 evidence remains applicable to the unchanged application source. No new stable release or version bump is included. Test14 remains the published test APK; its tag source archives still reference the original baseline.

Salon Test14: persistent on-device metadata cache for Films/Séries catalogs, categories, visited movie details and episode lists. Cached content renders before network; entries younger than6h avoid repeat requests, older entries refresh on entry while retaining useful content on network failure/empty replacement. Account-isolated binary storage uses atomic bounded writes,64MiB maximum perentry and128MiB total, evicting older details before catalogs. Videos remain streaming; artwork uses existing Coil cache. Build/install,61tests and lint pass (0 errors,25 existing warnings,3 hints). Native normal/130% cold-restart, fresh-request suppression, stale-error, successful update and empty-response scenarios recorded in `.omo/evidence/library-cache/QA.md`. Physical GoogleTV/performance timing/playback and full-offline artwork are not certified.

Salon Test13: removed the redundant lane/category menu chip from the live toolbar; destinations remain in the sidebar. Pays/Qualité/search begin at the content edge, and Up from the first channel reaches Pays. Country persists per lane/provider category across navigation and cold restarts; existing global quality persistence retained, including global-search expansion. Tous/Toutes resets persist too. Build/install,55 tests and lint pass (0 errors,25 existing warnings,3 hints). Updated the stale removed-tab test to validate the actual sidebar destinations.19 fresh normal/130% native captures verify selection, independent lane memory, cold restart, explicit clears, dialogs and search Back: `.omo/evidence/live-filter-memory/QA.md`. Native category persistence is covered by key unit test/source trace rather than a separate UI scenario. Physical GoogleTV/playback remain external verification limits.

Salon Test12: Home now starts with live shortcuts, then favorite channels, then the mixed film/episode À reprendre row, followed by pinned categories and recent channels. Initial and Back fallback focus targets France; Ma liste retains its existing order. Build/install and lint pass (0 errors,25 existing warnings,3 hints). Six fresh normal/130% native Redroid captures verify section order, Down navigation and drawer return: `.omo/evidence/home-resume-order/QA.md`. Layout-only change; physical GoogleTV and playback not re-certified.

Salon Test11 published: user clarification removes the entire Home hero as well as Pour vous: no channel/programme heading, direct-resume/explore button or reserved height. Home starts with film/episode À reprendre when available, otherwise live shortcuts; initial/Back fallback focus follows this order. Removed unused hero schedule request, preserved background selection, Ma liste heading and lower recent channels. Build/install and lint pass (0 errors,25 existing warnings,3 hints);10 fresh native normal/130% captures cover populated/empty resume and remote focus/return. Synthetic history backed up/restored for empty-state QA. Two independent scoped PASS reviews and evidence: `.omo/evidence/home-without-hero/QA.md`, `REVIEW_A.md`, `REVIEW_B.md`. Layout-only change, no new tests or physicalTV/playback certification.

Salon Test10 published: sidebar returns to main's grouped direct destinations (search first; France, Português, Sports, Événements, Favoris; library; guide/categories/settings), pill focus and full-height dark overlay with gradient scrim. Compact rail/content geometry and full-width artwork retained, scroll cue added. Home « Pour vous » header row and its reserved height removed; Ma liste heading retained. Build/install and lint pass (0 errors,25 existing warnings,3 hints), with28 fresh native screenshots across shared shell pages,130% font and virtual4K. Two independent scoped PASS reviews: `.omo/evidence/main-sidebar/REVIEW_A.md` and `REVIEW_B.md`; scope/limits in `QA.md`. Layout-only change adds no unit tests; physicalGoogleTV/playback not re-certified.

Salon Test9 published: foreground automatic update checks (enabled by default, at most every6h in the current process), Home-only update offer and manual check/opt-out in Réglages. Explicit download with progress/cancel, HTTPS GitHub allowlist, bounded size/SHA-256 and exact installed-package/newer-code/signing-certificate validation precede native Android installation consent. Stable and Salon use separate manifests. Build,54 tests and lint pass (25 existing warnings,3 hints). Native Redroid12→13 update from the identical frozen code succeeds with saved account/settings; cancellation, metadata404, invalid hash, current-version result and toggle persistence are verified. All22 normal/130% native captures and two independent scoped PASS reviews: `.omo/evidence/app-update/QA_FINAL.md`, `APPROVAL_A.md`, `APPROVAL_B.md`. Redroid is a phone image: the system permission grant used ADB; its D-pad interaction on physical Google TV remains unverified. The stable publishing workflow is prepared locally, not merged or run.

Salon Test8 published: Home/Films/Series artwork now fills the entire screen behind the navigation rail. AppDrawer no longer pads the background on those pages; the same16dp inset belongs to foreground content instead, with compensated Home card widths. Text/cards keep their previous positions and dimensions; other pages retain their shared inset. Build/install,15 fresh native normal/130%/virtual4K captures, independent integrity/visual reviews and public APK checksum verification pass. Evidence: `.omo/evidence/full-width-background/QA.md`. This layout-only change adds no unit tests and does not re-certify physical-TV playback.

Salon Test7 published: all six approved follow-ups are integrated. Back consumes the remote key before the IME and restores visible search focus; live rows reclaim name width with smaller logos and preview-only country/quality badges. Selected preview and compact3s fullscreen banner show cached provider current/next titles, times and progress. Home leads with last live and a single mixed movie/episode resume row, then shortcuts, favorites/pins before recents. Playback errors distinguish network/provider/busy/format, with direct preview retry and initially focused fullscreen/VOD retry; global network/busy faults do not penalize individual channels. Channel-scoped retry jobs cancel on zap/background/exit, and rebuffer keeps the video surface. Resume cards size from individual text line heights, including Android14 nonlinear font scaling; their focused bounds include progress/time, keeping the entire item visible at130%. Build,49 tests and lint pass (25 existing warnings,3 hints). All40 final native captures, independent integrity/visual PASS reviews and public download verification are recorded in `.omo/evidence/daily-remote/QA.md`. Physical-TV decoded-video continuity/listening remain external limits.

Salon Test6 published: physical-TV feedback requests a smaller leading gutter, much larger audible preview and no reload on opening the currently previewed channel. Rail starts12dp from the edge rather than58dp, content starts74dp rather than114dp, and live list/preview split is40/60 rather than57.5/42.5. LivePlaybackSession owns one ExoPlayer across preview/fullscreen and transfers its target view; settled empty results/background stop it and leaving live releases it. Build,41 tests and lint pass. Native audio-only fixture verifies identical AudioTrack/session and unchanged request count across preview→fullscreen→preview, plus silent empty/background states; Redroid's cross-codec graphic-buffer error14 prevents video-frame continuity verification. All32 final captures at normal/130%/virtual4K and two fresh scoped PASS reviews are under `.omo/evidence/live-continuity/`. Public Test6 download checksum verified; physicalTV video continuity/listening remain external checks.

Salon Test5 published: common compact navigation rail across browsing screens, expanding over content with labels on focus; global top header removed. Home uses an unboxed resume/direct action over edge-to-edge background. Movie/series libraries show settled focused-title artwork, title/metadata/overview above8/6-column grids; search/category/sort share a compact row and categories open in a left panel. Home/saved/category footer strips removed; direct keeps visible Pays/Qualité and56dp minimum rows, growing for long names/status/enlarged text. Native review repaired live Back ownership, restored28dp shared top margins and added8dp episode focus clearance; Right returns from rail to content. Build,41 tests and lint pass (25 existing warnings,3 hints). Final41 captures at normal/130%/virtual4K and two independent scoped PASS reviews are under `.omo/evidence/unified-tv/`. IME first-attempt irregularity, player initial error focus and physicalTV/real-video limits remain explicitly recorded. Public Test5 download verified; details below.

Salon Test4 fullscreen correction: removed the permanent bottom remote-help footer and position counter from live browsing; list/preview now use the recovered height inside TV safe margins. The normal profile fits seven complete programme rows. A separate native reproduction found Android navigation bars reappearing after IME/modal focus changes; MainActivity now reapplies immersive system-bar hiding when window focus returns. Build,41 tests and lint pass (25 existing warnings,3 hints). Final native capture inventory/review: `.omo/evidence/live-fullscreen/QA.md`. Real-provider playback and physical Google TV assessment remain pending. Older Test3 entries below are historical.

Salon Test 3 correction: the physical-TV photo rejected Test2's three stacked header layers. Live browsing now has exactly one toolbar: current lane/menu, Pays, Qualité, search and count. Global and live destinations move to an on-demand left panel; editing search replaces controls within the same toolbar and returns them on IME Back. The list starts immediately below. Native default/130% text and virtual 4K captures verify country/quality selection, scoped/global search, panel dismissal/reselection, Home/Films, guide/player return, favorites and category-parent return. Build, 41 tests and lint pass (25 existing warnings, 3 hints). Final test APK is `0.2.1-salon-test3`, versionCode 6; SHA-256 `e4ad87e9daa1909541d4ddf9d89de5faff3b6770aae724d37bfa530179afd007`. Evidence: `.omo/evidence/live-single-toolbar/QA.md`. Physical-TV judgment and real-provider playback remain pending. Test2 entries below are historical.

Distribution: Salon Test15 is published in GitHub prerelease `salon-test-20261018`, replacing `canape-salon-google-tv-test.apk` at the unchanged URL used by Downloader code **9612433** (`https://aftv.news/9612433`). SHA-256 `e95ac8c5049c54b6d2a0da5d35b98082ee2dc90b3cf4819c7d34091fa8066f58`. VersionCode19, version0.2.1-salon-test15, same test package/certificate. Test9 or later can discover it through the in-app updater. Two scoped integrity/visual reviews PASS/APPROVE with no blockers; test-coverage limits and evidence are recorded in `.omo/evidence/sidebar-focus/QA.md`. Main integration above covers Test1–14; Test15 changes remain in the Salon worktree. No new stable release is published.

Physical Google TV feedback (2026-10-18): the user installed Salon Test and reported excessive whitespace across screens and apparently missing language/quality filters. Follow-up makes live filters permanently visible, retains minority-country options, compacts shell/Home/details and increases library grids to eight normal/six enlarged columns. Scoped live search expands on demand, recovering a full row. The `0.2.1-salon-test2` build (versionCode 5, same test application ID/certificate) passes build, 41 tests and lint (25 warnings, 3 hints), installs over the earlier test and passes native D-pad checks with fresh screenshots at default/130% text plus a 3840x2160 display check. Binder/ADB was restored after user action and container restart. Real playback and the updated build on physical Google TV still require user testing. Current evidence: `.omo/evidence/tv-density-followup/RUNTIME.md`; older Salon captures below describe the initial build.

Google TV test package (2026-10-18): `0.2.1-salon-test`, application ID `fr.channelfinder.tv.salontest`, launcher label **Canapé Salon Test**. A separate debug-signed APK permits installation alongside the public release because its release signing key is unavailable locally. Build, APK signature verification, Redroid installation and fresh setup launch pass; screenshot: `.omo/evidence/salon-redesign/test-apk-setup.png`. Account setup is separate from the existing app. Physical Google TV testing remains pending.

Salon redesign: warm charcoal/ivory/amber identity, a short top navigation, compact Home resume hero, live lane tabs and persistent movie/series selections in **Ma liste**. The existing sofa/C artwork is retained. Local debug build, 38 unit tests and lint pass (25 existing lint warnings). Native Redroid checks cover normal/130% text, navigation, guide, search, categories, favorites, saved-title add/remove/persistence and return focus. Playback from the synthetic local fixture stayed buffering; real-provider playback, on-device resume checkpoints and physical Google TV are not revalidated by this pass. Evidence and exact limits: `.omo/evidence/salon-redesign/QA.md`. This work does not publish a release.

Branding update (2026-10-13): the user-selected sofa/C icon is used in Android launcher resources, setup/loading `AppMark`, and the TV launcher banner. The README uses the approved horizontal wordmark. Signed release build and lint pass with existing warnings; setup and focused launcher screenshots are verified on the isolated Google TV emulator. This source update does not publish a new APK release.

## Product summary

Canapé is a Google TV / Android TV app for finding live channels in a large user-supplied Xtream Codes playlist. It is designed for a viewer who finds typical IPTV apps difficult to navigate. The playlist currently contains 24,057 live channels, so the product prioritizes direct destinations, local search, simple favorites, and a live preview while browsing.

The project is a working prototype. Provider playback has been verified in earlier emulator runs; user testing on physical GoogleTV drives the Salon redesign. Live previews are now audible and share their player with fullscreen. Test6 uses a12dp outer rail margin and74dp leading content edge by user request; right58dp and top-bottom28dp remain. Current Redroid cannot map decoded video buffers, so final Test6 video-frame continuity and physical-TV audio quality remain external checks. D-pad focus and native TV controls remain required.

## User and problem

The primary viewer is a family member using a TV remote, not a phone or mouse. He wants to find French and Portuguese channels quickly, with sports easy to reach. The playlist is much larger than can be browsed comfortably as one long list. There is no separate EPG source; programme schedules now come from the saved Xtream provider.

The interface must work with D-pad focus and OK/Back actions. It should make the next useful action obvious and avoid requiring the viewer to understand provider category IDs or Xtream terminology.

## Goals

- Make French, Portuguese, sports, channel favorites, recent channels, and pinned categories reachable directly from Home.
- Let viewers search the local channel catalog without waiting for another provider request.
- Let viewers pin a provider category from a searchable category picker and reopen it from Home.
- Show an audible live preview for the focused channel, then retain its running player when opening that same channel full-screen with OK.
- Keep browsing responsive and predictable with a playlist of roughly 24,000 channels.
- Store the account securely at rest and keep account details out of source code and documentation.

## Non-goals for the current release

- A program guide grid, recording, catch-up TV, parental controls, or channel editing.
- Providing or hosting any TV content. The app is a client for a user's own service account.
- A phone-first interface or touch-only interaction.
- Automatic metadata enrichment from third-party services.

## Core experience

### Application updates

Réglages → **Mise à jour de l’application** checks the official GitHub manifest manually. **Vérification automatique** persists the viewer’s choice; automatic offers appear on Home, never over playback. **Télécharger** retrieves and verifies the APK; **Retour** or **Annuler** cancels it. **Installer** opens the Android source-permission screen when necessary, then the Android confirmation. The account and preferences remain when updating the same application. Existing Test8 users must install Test9 once through Downloader to gain this feature. No silent installation or background scheduled service is implied.

Publication contract: generate metadata from the actual signed APK with `scripts/update-manifest.sh`, publish the APK first, then its matching manifest. Salon uses fixed prerelease `salon-test-20261018/update-salon.json`; stable uses `releases/latest/download/update.json`. Package identities never cross channels, and updates must retain the installed certificate and increase versionCode.

### First run and account setup

**Configurer avec mon téléphone** is available during setup and in Réglages. It opens a temporary QR code for a browser form on the same local Wi-Fi. The form can submit a new account, choose French/Portuguese/automatic audio, refresh channels, reset unavailable channels, and clear recents with explicit confirmation. The TV validates and encrypts account credentials through the existing connection flow; the browser never receives saved credentials. Check account validation results on the TV. The random session link expires after 10 minutes and closes with Back, navigation away or backgrounding. Local HTTP is unencrypted and requires trusted Wi-Fi; guest-network isolation can prevent access. Physical-phone access remains to be verified on a real TV network.

Verified on 2026-10-13: debug build, unit tests and lint pass; Settings QR dialog inspected with D-pad at normal and 130% font scale. The QR decodes, serves the browser form through emulator port forwarding, accepts audio settings, and closes its server on Back. The browser form was inspected at 390 px width. Fresh account setup with real credentials and a physical phone/TV LAN were not tested.

1. The viewer enters the Xtream server address, username, and password. Focusing a field never opens the keyboard; OK starts editing, and the keyboard's Next action moves to the next field. An address without a scheme gets `http://`.
2. The app validates the account by loading the catalog, and only then saves it encrypted with AES/GCM using a key held by Android Keystore.
3. Home opens. The catalog is cached on device (`CatalogCache.kt`, metadata only), so later launches open Home from the cache and refresh silently in the background.
4. Connection, authentication, and unexpected-response failures are shown in plain language on the form, which keeps what the viewer typed. A failed load for a saved account shows **Réessayer** / **Modifier le compte**.
5. **Réglages** (gear on Home) offers: refresh the channel list, change the account (server shown, never the username), and clear recents.

The setup form scrolls inside its own pane. Focus reveals fields and the connection action with enlarged text or an inline validation error; the HTTP warning stays in the form. One Back closes the keyboard and ends editing.

Many Xtream providers only offer HTTP and redirect stream requests to other servers (often bare IP addresses), so the app permits cleartext traffic to any host. The setup screen warns that HTTP does not protect account details or streams in transit. Keep this warning visible; encrypted local storage does not encrypt network traffic.

### Main navigation

All browsing screens share the compact left icon rail, grouped like main: Rechercher first; Accueil, France, Português, Sports, Événements and Favoris; Films, Séries and Ma liste; Guide TV, Catégories and Réglages. Focusing the rail expands pill rows over a full-height dark panel and gradient scrim without moving content. The menu scrolls to keep the focused destination visible, with a directional continuation cue. Live browsing retains one toolbar with current lane, Pays, Qualité where applicable, search and count; its contextual panel also exposes live sections, guide and categories. Home starts directly with film/episode À reprendre or live shortcuts, with no Pour vous/header/direct-resume hero; Ma liste retains its heading. Playback, full-screen guide, setup and loading remain full screen without the rail.

Back in root content focuses the common rail after scroll/editing steps. Right or reselecting the current rail destination returns to content with focus restoration. Back in the rail returns Home, then leaves the app from Home. Contextual dialogs own their own dismissal; sub-pages return to their parent. The live Back handler is enabled only while its content owns focus. Each live search searches its current scope; Toutes les chaînes searches all live channels.

### Home

Compact layout verified on the 1080p Google TV emulator: five complete channel cards fit across Home instead of four, with 54 dp artwork, shorter destination cards and 12 dp gutters. Browser, guide and category lists use tighter padding and 4 dp row gaps. Typography and TV safe margins remain unchanged; rows grow with programme summaries and enlarged text. Home, browser live preview, guide and Categories were inspected with D-pad navigation; Home, guide and Categories were also inspected at 130% font scale. Physical-TV readability remains unverified.

Home answers “what can I watch quickly?” A compact hero resumes the latest unfinished movie/episode, otherwise reopens the last live channel, otherwise offers En direct. Provider artwork is used for on-demand titles; a bundled generic coastal image provides Home atmosphere without claiming to depict a programme. Four monochrome icon shortcuts preserve France/Português/Sports/Événements access. Unfinished movies and series have separate resume rows, followed by recent channels, favorite channels and pinned categories; guide/categories actions remain available below. The hero starts focused; Down explicitly reaches France. Returning restores focus and scroll. Back moves to the Home tab before exiting.

**Ma liste** groups saved films, saved series, unfinished on-demand viewing, channel favorites and category pins under separate headings. Movie/series detail screens expose **Ajouter à ma liste / Retirer de ma liste**. Saved titles persist locally per hashed account, capped at 500, independently of viewing progress. Removing a saved title from its detail screen returns to the surviving selection or the visible favorite-channels action. Empty states explain how to populate the list. No recording, downloading, recommendations or third-party metadata is implied.

Pinned category shortcuts show their category label and channel count. Selecting a shortcut opens the category's channel list directly. Category favorites are stored locally by Xtream category ID, not by parsing the category label.

Home keeps contextual OK/long-OK hints below its scrolling content and shows the focused channel/programme or category. Channel cards prioritize two-line names and programmes instead of repeated category labels; cards share the tallest height in each row. Removing a favorite or pin transfers focus to the nearest surviving card. The last favorite returns focus to France; the last pin focuses Épingler. Pinned and empty cards grow for enlarged text.

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

Preview actions use compact buttons so Regarder, Guide TV and the favorite star share one line at normal text size. The star retains its accessible action name and does not expand on focus. Enlarged text and the additional Retirer action may wrap while preserving remote navigation.

The bounded landscape preview fits the available pane height; video preserves its aspect ratio inside it. Compact loading/error overlays omit extended explanations when height is limited. Quality and country have explicit toolbar controls with modal choices; active restrictions remain visible in the controls and count. The list and preview keep independent scrolling. Up from the first channel reaches the toolbar menu.

Home's recent/favorite channel cards also show current titles when supplied. Browsing and Home add no placeholder line for missing, failed or pending programme data; the guide retains its explicit states.

Regular channel rows show the current programme below their metadata in France, Português, Sports, favorites, recents, search and category lists. Event feeds retain their existing event information. Programme requests remain limited to composed rows, settle for 650 ms, and share the guide cache; missing schedules do not hide channels in browsing.

The browser uses two panes: a focusable channel list40% on the left and a16:9 live preview60% with details/actions on the right. Moving focus immediately stops the previous selection, then starts the audible preview after650ms without another focus change. Regarder or OK on the loaded channel retains its ExoPlayer/media item and transfers the surface to fullscreen; Back retains the stream in preview. Audio follows system focus/output disconnect, stops in background or settled empty results, and is released when leaving live screens. Favorites use long OK on rows or the preview star. Right from the list reaches Regarder; Up from the first row reaches the single toolbar; Left restores the selected row. Provider logos are bounded256px with initials fallback; cleaned names, country/quality badges and stars remain. Selection stays highlighted while focus is in preview actions.

A **Qualité** toolbar control opens the choices Toutes, 4K, Full HD, HD and SD, read from channel/category metadata. Untagged channels appear under Toutes. The choice is remembered between sessions; an empty result offers **Toutes les qualités**.

Quality is not applied in Événements, Favoris and Récents (events rarely carry a quality tag; favorites and recents are the viewer's own picks), and its control is hidden there.

A **Pays** filter sits in the same row whenever the list on screen (lane or category) mixes at least two countries with two or more channels each, for example Brésil/Portugal in Português or the countries of `LT | LATINO`. Countries come from the channel's prefix, else its category's, mapped to ISO codes (IND→IN, USA→US…); provider prefixes that are regions or languages (EU, LA, AS, AF, AR…) are never shown as countries and such channels only appear under **Tous**. The country choice belongs to the current list and resets when the lane or category changes.

The country dialog retains every recognized country, including single-channel minorities, and provides search instead of capping visible choices. Long names use short French forms (Centrafrique, États-Unis…). Home lane counts follow the saved quality filter ("340 en Full HD").

Lanes list the viewer's own countries first (Português: Portugal then Brazil; Sports: France, Portugal, Brazil), then general-interest groups. Back in a scrolled list returns to the top first; a second Back leaves. In Récents the preview offers **Retirer** to drop one channel; clearing the whole history in Réglages needs a second press. Category screens show their channel count in the header and do not repeat the group name on each row. Favoris, Récents and Événements have no quality or country filter.

Search ranks matches (exact name, then name prefix, then word prefix, then anywhere), ignores accents, and requires every typed word to match. Channel names are cleaned for display only (`ChannelText.kt`): country prefixes such as `|FR|` become a badge and quality suffixes (HD, FHD, 4K, HEVC…) become badges. Raw names are still searched.

**Chercher partout** preserves the query and removes lane/category, country and quality restrictions. Clearing quality also updates the saved quality preference; the unrestricted state remains visible in the filter chips.

When returning from full-screen playback, restore the selected channel and list position. While the stream buffers, show a clear progress message. If preview playback fails, explain that the viewer can still try full-screen playback. An empty search result should remove the preview and provide a clear empty state. The D-pad must be able to move from the search field into the results.

### Full-screen playback

Media3/ExoPlayer plays the selected live stream with sound; there is no seek bar (live TV). A channel banner (logo, name, badges, position in list, clock, previous/next channel) shows on start and on any key, and hides after 5 s; OK toggles it. Up/Down (and CH+/CH−) zap through the list the channel was opened from; the banner follows immediately and the stream switches once the viewer pauses (250 ms). A failed stream is retried twice silently; then a card explains the failure in plain language (HTTP 407, 404, connection limits, unsupported format…) with **Réessayer** and **Chaîne suivante**. Back returns to the previous screen (an in-app back stack) with the last watched channel selected.

### TV guide

**Guide TV** opens from the drawer (France channels with the saved quality filter), the preview action (the current list), or **Left** during playback. The dedicated Guide key is also handled when forwarded by the device. The full-screen guide has a channel column, chronological programme list, and programme description/direct-play pane. Focusing a channel loads its schedule; Right enters the current or next programme. Up/Down browse programmes and update details without opening a stream. Right reaches **Regarder le direct**; Down from that button enters the scrollable description. Left returns to the selected programme/channel. Back returns to the screen that opened the guide. Watching from a browsing guide keeps it under the player so Back returns to it; watching from a playback guide resumes/replaces the existing player rather than stacking another player.

Schedules use `get_simple_data_table` with the channel's stream ID. Base64 UTF-8 titles/descriptions are decoded, invalid intervals skipped, duplicates removed, and provider epoch times displayed in the TV's local timezone. **EN COURS**, **À VENIR**, and **TERMINÉ** use those intervals, not provider now_playing flags. Programme selection always plays the live channel; it does not replay a past programme or schedule a future one. Loading, empty data and connection errors are distinct; **Actualiser** bypasses the cache and errors offer **Réessayer**. Requests settle for 350 ms, cancel when leaving/changing channel, and retain at most 40 channel schedules in an account-isolated 15-minute memory cache. No new account entry or external service is required.

Guide channel rows also show the current programme title below channel/version metadata. Visible rows fetch schedules after 650 ms, with at most two background requests in addition to the selected channel; the whole playlist is never fetched at once. Titles use the programme intervals, rechecked every 30 seconds, and schedules are revalidated every 15 minutes. **Actualiser** refreshes the selected schedule and composed row summaries. Rows distinguish **Chargement…**, **Programme non fourni** (no current interval), and **Guide indisponible**. The channel column is 240 dp; titles truncate to one line to preserve density. Provider coverage remains a limitation.

Category channel lists use the same current-programme summaries below country/quality badges, keeping every channel available for live playback. The guide defaults to **Toutes** and retains the requested channel even without a schedule. Its header names the originating scope and channel count. **Programme en cours** excludes non-active channels known to have no current interval or a guide error, but preserves pending rows and the active channel. Filter availability freezes while navigating the channel pane and updates after leaving it, avoiding asynchronous row removal under focus. Manual refresh and the 15-minute schedule check revalidate availability. This filter concerns programme data, not stream playback availability.

The earlier Portuguese sports check verified supplied programme titles, missing-programme fallback and manual refresh at normal and 130% text. The 2026-10-13 usability change additionally verifies MY TF1 remains selected with its empty schedule under both Toutes and Programme en cours; direct playback remains available.

Verified on the Google TV emulator at normal and 130% text size: live titles for TF1, M6 and France 2, missing schedule fallback, D-pad access to future programmes and scrollable details, and manual refresh. Selecting a future programme keeps the live title in the channel row. Debug build and all five guide unit tests pass. Physical-TV readability and programme rollover have not been observed in this check.

### Android TV design guide and quality checklist (reviewed 2026-10-12)

The whole TV design guide (foundations, system, styles, components), the TV app quality checklist and the TV accessibility guide were reviewed against the app.

| Guideline | How the app meets it |
| --- | --- |
| TV-PP / TV-PC (playback keys) | Play/Pause, Play and Pause keys pause the stream ("EN PAUSE" + pause icon); OK or Play resumes **at the live point** (no timeshift on live IPTV). OK otherwise toggles the channel banner, the usual live-TV behaviour; Left/Right seeking does not apply to live streams. |
| TV-NP (playback after leaving) | Leaving the app (Home, another app, screensaver) stops the full-screen stream and the preview, which also frees the account's single provider connection; returning reloads at the live point. No "Now playing" card is needed because nothing plays in the background. |
| TV-BU / TV-BY (screen sleep) | Only full-screen playback keeps the TV awake; the muted browsing preview does not block the screensaver. |
| Audio | Full-screen playback takes audio focus (the Assistant and other apps duck or pause it) and pauses when the audio output disconnects. Preferred audio languages: the viewer's last choice, then French, then Portuguese. |
| Lists guideline | Single-choice lists use radio indicators. The row shown in the preview carries an "▶ Aperçu" indicator, not only a background colour. |
| Buttons guideline | One main action per screen as a filled button, placed first; secondary actions outlined; focused buttons grow 1.1× with a light fill and no glow or shadow. |
| Cards / layout grid | Four Home columns share the actual width after the drawer and 58 dp side margins (186.5 dp on the 960 dp TV canvas), with 20 dp gaps. Lane cards stay visible without horizontal scrolling. |
| Accessibility (text scaling) | Verified at 130 % system text size: cards grow with their text and lane titles drop their small emoji instead of clipping. Text size, theme, density and locale changes no longer restart the app. |
| TV-SB (search) | In-app search from the drawer and each list. The remote's search/mic key is handled by Google TV itself and never reaches the app. |
| Not applicable | Featured carousel and immersive list need large artwork (the provider only supplies small logos). Watch Next / Live tab are not used: Google TV does not show app channel rows and Watch Next targets resumable on-demand content. |

In full screen, **▶** opens **Audio et sous-titres** when the stream has more than one audio track or any subtitles; the remote's **Last channel** key returns to the previous channel.

### Review 5 (2026-10-12)

- Home lane cards and channel cards share four columns calculated from the real drawer-inset content width. The first lane no longer scrolls underneath the rail when the fourth lane is focused.
- Browsing adds contextual remote hints and selected position / result count. Empty results say **Choisir**, not **Regarder**. Preview actions show **◀ Liste**; Left explicitly restores the selected row even when enlarged text wraps the favorite action. Right/Down move through actions, including Retirer in Récents; Down from the final action restores the row.
- Preview loading names the operation; a failed preview explains that full-screen playback can still be tried. Upcoming events describe availability without promising automatic startup.
- Preview metadata/actions wrap and its pane scrolls independently. Channel/category rows, chips and pin controls grow for enlarged text; settings subtitles allow two lines. Unavailable badges use a darker error color on focused light rows. Selected filters and pin actions have accessibility semantics.
- Text editing opens the keyboard as soon as focus succeeds, instead of waiting through the remaining retry loop. Country-filter empty results offer **Tous les pays**. Recovery buttons restore focus into results after clearing search or resetting quality/country.
- The GUIDE marker is removed until actual programme data is available. Provider EPG IDs remain in the model.
- Build and Android lint pass. Detailed emulator scenarios, captured states, limitations and review verdict are recorded in [the UI/UX review](qa/ui-ux-2026-10-12/REVIEW.md).

### Review 4 (2026-10-12)

- Every screen's initial focus retries until it lands (`focusWhenReady`), and each lane/category is its own screen, so choosing a list from the drawer always moves focus into it. Previously focus could stay in the drawer, and Back then went to Accueil.
- Lists show their search field only from 15 channels (Favoris usually has none).
- The preview's favourite is an outlined star icon button that reads "Ajouter aux favoris" / "Retirer des favoris" when focused. Match feeds cannot be favourites (no button, no long press).
- Événements is sectioned by day (En cours, Aujourd’hui, Demain, then dates); badges inside a section show only the time. The same match is merged when titles match and kickoffs are within 30 minutes (providers disagree by a few minutes). "A - B" becomes "A vs. B" only when neither side contains digits, so session titles such as "Day 1, Session 1" stay as they are.
- Trailing language tags in group names are tidied: "CANADA |FR" → "CANADA (FR)".
- Réglages: **Langue audio préférée** (Français → Português → Automatique) and **Réessayer les chaînes indisponibles** (clears all 12-hour marks). The list scrolls if it outgrows the screen.

### Movies and series

Category changes keep Continue Watching visible across movie and series categories, clear title search and reset the grid and focus to the top. Selecting the current category without an active search preserves the current position; Back from details or playback still restores browsing context.

**Films** and **Séries** are separate navigation drawer destinations. They load categories and titles only when opened, using the saved account and the provider's `get_vod_categories` / `get_vod_streams` and `get_series_categories` / `get_series` actions. Category filters and accent-insensitive title search run locally. Selecting a series fetches `get_series_info`; episodes are ordered by numeric season and episode, with a season filter. Movies use `/movie/…` and episodes `/series/…`, preserving the provider container extension. Full-screen Media3 controls support remote pause and seeking. Back restores the catalog, filter, selected row and scroll position, including episode lists.

The VOD interface uses six poster columns (five at enlarged text), scoped search, a searchable category overlay and Trier. Provider order, title A–Z and descending known year are available; unknown years stay last. Each library retains its own sort while open; changing it preserves category/query and resets position. Explicit year/quality suffixes become separate metadata while original titles remain searchable; numeric or ambiguous titles are preserved. Down from header enters the first content card. Native grid scrolling reveals the whole focused poster with clearance for its outline. Movie details fetch provider synopsis/backdrop/year/rating via get_vod_info and offer Reprendre or Recommencer. Series show season chips and three episode columns (two at enlarged text), with individual progress and a next-episode action. Watch progress is stored locally under a SHA-256 account key, capped at 100 metadata records. It saves every five seconds, on pause/seek/background and exit; actual end or 95% watched marks completion. Continue Watching shows unfinished movies and each series' most recently watched episode if unfinished, survives app restarts, and supports long OK to remove an entry. No cross-device sync is provided.

Catalogs remain in account-isolated memory and reload after an app restart. They have no live preview, EPG requests, live-channel favorites or live recents. Pending requests disconnect on leaving the screen. Empty catalogs and provider errors have French explanations; failures offer retry. No external metadata service is used.

Verified on the 1080p Google TV emulator at normal and 130% text: six/five-column poster grids, three/two-column episodes, complete focus outlines, cinematic movie details, category search/filtering and empty-result recovery. Movie resume persisted through reinstall/restart; Recommencer reset playback, and series episode progress appeared in Continue Watching and the episode grid. One Back closes the keyboard. The debug build and all 11 unit tests pass, including persistence, completion and catalog/episode parser tests. A provider 405 playback refusal displayed a safe French error and retry. Physical-TV readability, catalog failure/retry and an actual end-to-next-episode transition remain unobserved.

## Functional requirements

| ID | Requirement | Current status |
| --- | --- | --- |
| FR-1 | Load live categories and live streams through the Xtream player API. | Verified on emulator: 24,057 channels load. |
| FR-2 | Keep channel ID, category ID, category label, logo URL, EPG ID, and derived lane with each channel. | Implemented. |
| FR-3 | Search channel names, category labels, and lane labels locally. | Verified: "TF1" returns 36 ranked results, exact match first; D-pad leaves the field. |
| FR-4 | Classify French, Portuguese, and sports channels into direct Home lanes. | Category country code wins over name words (2026-10-11): France 1,870, Português 769 (now includes `BR |` groups), Sports 391 (French/Portuguese only since 2026-10-11), Événements 423 after de-duplication. |
| FR-5 | Pin/unpin categories and open them directly from Home. | Verified on emulator: pin from picker, card appears on Home. |
| FR-6 | Pin/unpin channels and retain a recent-channel list. | Verified on emulator: long-press favorite, Home row, recents ordered by recency. |
| FR-7 | Preview the selected live channel audibly after650ms settled focus, in a larger landscape pane. | Test7 native AAC fixture: READY, active non-muted AudioTrack; rebuffer keeps the surface with a discreet spinner; network/format/busy messages and direct retry verified. Normal/130%/virtual4K layout checked; physicalTV video/audio quality pending. |
| FR-8 | Open the currently previewed channel full-screen without reloading, retaining context on Back. | Test7 native AAC fixture: unchanged request count and identical AudioTrack/session across both transitions. Compact3s banner and large-text error focus/retry verified. Actual video-frame continuity blocked by Redroid graphics error14, pending physicalTV. |
| FR-9 | Display provider programme schedules with times, current/upcoming status and descriptions. | Test7 adds current/next titles, times and progress to preview/fullscreen using cached provider data. Native populated/empty EPG and interval/progress unit tests pass. Existing full guide remains available. |
| FR-10 | Browse provider movies separately with categories, search and on-demand playback. | Implemented; emulator catalog exposes 53,538 movies and movie playback verified. |
| FR-11 | Browse provider series separately and select seasons and episodes. | Verified on emulator: series catalogs, season/episode lists, episode playback and Back restoration. Choosing Séries hands focus to content; opening a series keeps the drawer collapsed while episodes load. |
| FR-12 | Offer coherent navigation without stacking headers over content. | Test10 restores main-inspired grouped direct destinations and pill overlay, retaining compact/full-width geometry; Home header removed.28 fresh normal/130%/virtual4K native captures under `.omo/evidence/main-sidebar/`; physicalTV verdict pending. |
| FR-13 | Save movies/series separately from playback progress and find them in Ma liste. | Local fixture: add a movie and series, survive reinstall/restart, open their details and remove the movie with visible recovery focus. Serialization/kind-separation tests pass. Real-provider titles pending. |
| FR-14 | Expose language/country and quality filters directly while browsing live channels. | Test5 retains named Pays/Qualité controls in the single toolbar beside common rail, options in dialogs. Final native evidence under `.omo/evidence/unified-tv/`; minority-country regression tests unchanged. |
| FR-15 | Discover and install official application updates without returning to Downloader for every release. | Test9: automatic foreground check, manual check/opt-out, verified download/cancel and native Android installation. Actual12→13 update retains saved account/settings;54 tests pass. Final normal/130% native evidence: `.omo/evidence/app-update/QA_FINAL.md`. Physical GoogleTV source-permission navigation remains unverified. |

## Technical design

- **Platform:** Native Kotlin Android app using Jetpack Compose for TV and Android TV launcher metadata.
- **Toolchain:** JDK 17, Gradle 9.8.1, Android Gradle plugin 9.4.1 (built-in Kotlin), Kotlin Compose compiler plugin 2.4.21, compileSdk 37, targetSdk 35, minSdk 23, Compose BOM 2026.09.00, Media3 1.11.1.
- **Navigation and screen state:** `Page` and `BrowseLane` in `Channel.kt`; the current screen flow is managed in `MainActivity.kt`.
- **Provider access:** `XtreamRepository.kt` calls `player_api.php` actions `get_live_categories` and `get_live_streams`. It maps `category_id` to `category_name`, and maps each live stream's `stream_id`, `name`, `category_id`, `stream_icon`, and `epg_channel_id` into a `Channel`.
- **Stream URL:** Live playback is built as `/live/{username}/{password}/{stream_id}.ts` by `XtreamRepository.streamUrl`. The provider redirects these requests to separate stream servers.
- **Playback:** `buildStreamPlayer` in `Playback.kt` creates ExoPlayer with VLC User-Agent, cross-protocol redirects and explicit timeouts. AppState owns LivePlaybackSession; audible preview and fullscreen transfer the same player/surface target. Stop on settled empty browsing/background, release when leaving the live family. Preview starts after650ms settled focus; classified retry policy and channel-scoped delayed jobs handle failures. VOD has an independent player and detaches native controls while showing an error or ended state.
- **Persistence:** `CredentialStore.kt` encrypts account data with AES/GCM and Android Keystore. Channel favorites, category favorites, and recent IDs use local SharedPreferences.
- **Performance:** The catalog and ID lookup map are held in memory (about 135 MB PSS in an earlier emulator check). Channel results render in a LazyColumn. Live sorting, country counts, ranked search and empty-result recovery run on Dispatchers.Default, as do movie/series search and category filtering. Calculations read a consistent Compose snapshot; cancelled queries cannot publish obsolete results. Category ranking is cached per label and cleared with the catalog's category labels. Home opens before a downloaded catalog is written to disk. Counts are grouped for Home and the category picker. Actual stream startup still depends on the provider and network.
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

- **Test8 full-width background (2026-10-18):** fixed parent padding that excluded the left16dp from Home/Library artwork. Full-screen canvas and preserved foreground geometry verified with native before/after,130% and virtual4K evidence. Broader pre-existing Home focus behavior is outside this scoped fix; the scrolled130% supplement shows a recent card extending below the viewport. Physical-TV assessment remains external.
- **Test9 updater (2026-10-19):** in-app installation, wrong-hash rejection, cancellation and persisted opt-out verified on Redroid. Physical GoogleTV source-permission navigation remains unverified; the phone permission switch required ADB grant. The Settings continuation hint can still point down at the last row because a small scroll inset remains; all rows are reachable. Stable CI changes are local only.
- **Test10 navigation (2026-10-20):** all restored destinations and shared shell siblings reached with D-pad; no Home Pour vous row. After returning Right from a lower nonselected menu item, the collapsed rail can retain its lower scroll offset, with the active Home icon above view; content focus stays visible and re-entering the menu focuses Home. PhysicalGoogleTV layout remains external.

- **Test7 daily remote verification (2026-10-18):** Live invisible focus after IME Back was reproduced in Test6 and fixed; repeated Live/Films and country/category dialog checks retain visible focus. Compact fullscreen hints no longer wrap Guide at130%, and error Retry starts focused. Home combines movie/episode resumes; nonlinear text sizing keeps episode labels visible at130%. VOD error controls were removed and native-to-Compose focus ownership explicitly transferred, fixing an underlying controls popup/initial focus failure.49 tests/build/lint pass; final40 native captures and review/distribution status are under `.omo/evidence/daily-remote/QA.md`. Physical-TV decoded-video continuity/listening and actual video-frame retention during buffering remain external checks; older Test5/6 focus/footer entries below are historical.

- **Test6 continuity verification (2026-10-18):** request/audio-session continuity, audible preview, settled-empty/background silence and larger live geometry verified with native evidence. Redroid H264/VP8 buffer mapping error14 prevents decoded-video continuity assessment; AAC-only black frames/loading overlay are not video success evidence. PhysicalTV surface transfer/listening and future-event runtime remain external checks. Existing fullplayer footer wraps Guide TV at130%; unchanged by Test6. An initial empty-result audio ownership defect was repaired before publication.
- **Unified TV composition (2026-10-18):** user-approved adaptation of programme-first reference-video hierarchy and AndroidTV principles. Common rail, provider artwork, compact controls and lateral categories verified in41 final normal/130%/virtual4K captures; two independent scoped PASS reviews. Live Back ownership,28dp top margins and episode focus clearance repaired. One initial IME Back opened the rail; controlled repeat restored search correctly, cause remains unresolved. Player error initially lacks focus; one Down reaches Retry. PhysicalTV and real-provider playback remain external checks.

- **Live bottom strip/fullscreen (2026-10-18):** physical-TV feedback rejects the persistent remote-help footer. Test4 removes it, recovering a seventh complete programme row on the normal fixture profile. Post-IME modal testing also reproduced a visible Android navigation bar; immersive hiding is reapplied on window focus regain. Native normal/130%/virtual4K and return-focus evidence under `.omo/evidence/live-fullscreen/QA.md`; physical TV remains unverified.

- **Three-header rejection corrected (2026-10-18):** physical TV photo exposed Test2's persistent global navigation, lane tabs and filter row. Test3 replaces them with one toolbar and a modal destination panel. Final-build native captures cover normal/130% text and virtual 4K, with restored focus and search/filter visibility. Physical-TV result and real playback require user testing; fixture streams have no video. Evidence: `.omo/evidence/live-single-toolbar/QA.md`.

- **Density/filter follow-up (2026-10-18):** first physical TV feedback rejected the initial Salon spacing and hidden filters. Test2 removes the expansion step for quality/language controls, retains all recognized countries, reduces headers/gutters, uses six Home channel columns and eight/six library columns. Fresh native default/130% captures cover eleven surfaces; a virtual 4K display fits the same logical TV layout. D-pad selection, country/quality recovery, search IME Back, favorites, guide access and episode-player return are checked with a synthetic catalog. This confirms layout/routing only; fixture video is absent, and the updated physical-TV result remains unverified. Evidence: `.omo/evidence/tv-density-followup/RUNTIME.md`.

- **Salon redesign verification (2026-10-18):** debug build, 38 unit tests and lint pass; 25 pre-existing lint warnings remain. Native normal/130% captures cover Home, direct/actions/filters, libraries, details, episodes, saved-title persistence/removal, guide, categories, settings and empty search. An old navigation focus request no longer steals focus on return from full-screen playback; episodes use compact headers/cards to retain complete outlines at 130%. Local synthetic video stayed buffering on Redroid even after removing audio; this pass does not establish real playback or on-device progress/resume. Existing progress unit tests still pass. Physical Google TV, viewing distance, large-provider performance and fresh account setup remain unverified. The synthetic art/titles used for QA are outside production code; only the generic brand background is bundled. Full evidence and limitations are in `.omo/evidence/salon-redesign/QA.md`.

- **Drawer opening from Home lane cards (2026-10-17):** opening France (or another lane) from its Home card briefly expanded the drawer, because the lane screen has nothing focusable while it computes its list and focus fell into the drawer until the list took it. After a page change not started from the drawer, drawer items cannot take focus until the content does, a remote key is pressed, or 3 s pass. Redroid recordings before/after verify the France card no longer expands the drawer; Left and Back still open it, and drawer selection still hands focus to content. Debug build and unit tests pass. Physical-TV behaviour remains unverified.

- **Menu transition flicker (2026-10-13):** drawer destinations replace the page without overlapping fades. New destinations restore their own focus; reselecting the current menu item still hands focus back to content. Movie/series loading and empty states have a search-field fallback. Before/after Google TV recordings verify France/Português transitions; Movies, Series and current-destination reselection retain visible content focus. Debug build and all 22 unit tests pass. Physical-TV transitions remain unverified.

- **Dense movie/series browsing (2026-10-13):** removed the discovery banner so the poster grid uses the full region below the search/category header. Poster focus keeps scale 1.0 and the white outline; removed its delayed extra scroll request. Debug build and unit tests pass. Google TV emulator screenshots verify both libraries, horizontal focus without card movement, and next-row scrolling. Physical-TV behavior remains unverified.

- **Removal confirmation and button focus (2026-10-13):** long OK on Continue Watching and the movie-details removal action now open the same confirmation dialog. Annuler starts focused; Cancel/Back preserve progress, while Retirer removes the entry and returns to browsing. The dialog shares the original-press release guard. Button glow/shadow is disabled while the light focus fill and scale remain. Verified on the 1080p Google TV emulator, with 22 passing unit tests and independent visual review.

- **Remote controls verification (2026-10-13):** drawer selection explicitly hands focus into content; series episode loading retains focus outside the drawer. Continue Watching removal consumes the remainder of the current OK/Enter press at the app root, preventing its release from activating the replacement card. Emulator checks cover Séries selection, opening a series, removing the last movie resume entry and the next ordinary OK. All 22 unit tests pass; the release-event regression failed before the guard was implemented. Physical remote testing remains unverified.

- **Category navigation verification (2026-10-13):** reproduced hidden movie Continue Watching before the fix. After reinstall, unfinished movies and the Luck S1 E1 entry remain visible in provider categories on the Google TV emulator. Back restores movie selection and position. All 19 unit tests pass, including a category change from grid item 120, independent movie/series sessions and unchanged-category restoration.

- **Responsiveness verification (2026-10-13):** live sorting/search and movie/series filtering now run off the UI thread. Search scans check cancellation between items, movie/series title keys warm in the background, and category ranks are reused. Debug build and 14 unit tests pass, including worker execution, snapshot consistency and cancellation. Emulator checks cover initial live focus, TF1 best-match selection, typing focus, missing results and muted preview. The 53,537-title movie catalog returns 25 Matrix matches while keeping focus in search; Back dismisses the keyboard. Zero-jank performance on a physical TV remains unverified; emulator cache-read timings still vary widely.

- **Reviewed usability verification (2026-10-13):** Home shows two-line names/programmes, equal-height cards and a fixed contextual footer; first/middle/last/sole favorite removal restores neighbor or France focus and consumes the held OK release. Pinned cards grow for wrapped names and sole removal focuses Épingler. Movies and series offer independent provider/title/year sorting, conservative year/quality metadata and deterministic header Down entry with full focused-card visibility. Guide entry retains MY TF1 without schedule, defaults to Toutes, states the originating scope and freezes filtered membership during channel navigation. Chercher partout preserves the query and clears country/quality restrictions. Blank setup was exercised in an isolated emulator user at 130% text with inline validation and keyboard/Back. Normal/130% native screenshots, 34 passing tests, debug build and lint are recorded in `.omo/evidence/review-usability/QA.md`. Existing lint warnings remain; physical-TV viewing distance and performance remain unverified.

- **Distribution (2026-10-13):** renamed to Canapé in the launcher label, banner, setup and settings. The existing application ID is retained. Local verification: 34 unit tests, debug/release builds and lint pass (existing warnings remain); the signed release installs on a clean Android 14 Google TV emulator, and setup/launcher screenshots show Canapé with its accent. Settings substitutions are source-reviewed. Signed APKs are distributed through `anthonymq/canape` GitHub Releases using a persistent signing key. The first release is signed locally; automated tag releases require signing secrets and `CANAPE_CI_RELEASES=true`. Downloader can use the stable latest-release APK URL (see README). Earlier debug installations require uninstalling before the first signed release.

- **Lane classification:** the category's country code now decides the lane when present; name keywords are only used for untagged categories. Sports keeps only FR/PT/BR-tagged groups (391 channels); other countries' sports (including `BE ‖ BELGIQUE • SPORTS`) stay reachable from their category and search.
- **Provider-side dead channels:** many France/Português channels answer HTTP 407 (see [Unavailable channels](#unavailable-channels)). The app learns them as the viewer meets them; there is no background scan, to avoid extra provider connections on a one-connection account.
- **Event times:** kickoff times in feed names are read in the TV's time zone; the provider's zone is not documented. Events are considered live for 3 hours after kickoff.
- **Partial catalogs from the provider:** on 2026-10-12 a background refresh received 70 channels instead of ~24,000 and replaced the saved catalog. A refresh that returns less than half of a catalog of 1,000+ channels is now ignored (logged as `Ignoring partial catalog`).
- **Catalog cache read time:** reading ~24k channels from the binary cache takes about 3 s on the emulator with a debug build. Measure on the target TV with a release build before optimizing further.
- **Logos depend on the provider:** many `stream_icon` URLs are empty or fail; rows fall back to initials.
- **Display-name cleanup is heuristic:** only known country-code prefixes and quality tokens are stripped. Raw names are still searched.
- **Preview may use an additional provider connection.** Verify provider connection limits on-device.
- **HTTP exposes traffic in transit.** Credentials are encrypted at rest, but the provider endpoint does not protect API or stream requests over the network.
- **Guide coverage depends on the provider.** Some duplicate feeds return no programmes (observed on MY TF1); TF1 returns a real schedule. The guide uses stream IDs, not assumptions about EPG IDs. No external XMLTV fallback, recording or catch-up playback is provided. The dedicated Guide key did not open the guide on this emulator; Left during playback provides the supported shortcut.
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
| `VodRepository.kt` | On-demand catalogs, typed movie/series/episode metadata and stream URL creation. |
| `LibraryScreen.kt`, `EpisodesScreen.kt` | Separate movie/series catalogs, category/title filters and season/episode selection. |
| `VodPlayerScreen.kt` | Full-screen on-demand playback, automatic resume, progress checkpoints and next-episode action. |
| `VodWatchStore.kt` | Account-isolated local watch progress, completion policy and Continue Watching selection. |
| `VodArtwork.kt`, `VodContinueRow.kt`, `EpisodeCard.kt` | Cinematic title banner, poster cards, resume cards and episode progress. |
| `MovieDetailsScreen.kt`, `LibraryCategoryDialog.kt` | Provider movie details and searchable category selection. |
| `Channel.kt` | Account, category, channel, lane, and page models. |
| `CredentialStore.kt` | Encrypted account persistence using Android Keystore. |
| `app/src/main/AndroidManifest.xml` | TV app, launcher banner and icon. |
| `app/src/main/res/mipmap-*/banner.png` | 16:9 TV launcher banner (with app name) and icon at every density. |
| `app/src/main/res/xml/network_security_config.xml` | Cleartext policy (all hosts). |
| `app/build.gradle.kts` | Android, Compose for TV, Media3, Coil, and Material icons dependencies. |
