# Channel Finder TV design system

## 1. Atmosphere & identity
A compact French-language TV library, legible from the sofa. Navy tonal layers keep live video prominent; a light focused surface or white artwork outline identifies the remote's current target. Preserve the existing lane colors and country flags, which identify the viewer's shortcuts.

## 2. Color
The authoritative Kotlin tokens live in `Theme.kt`, object `Palette`: Ink #0A0F18, InkTop #111A29, Surface1 #151E2C, Surface2 #1D2939, Outline #2F3D52, OnSurface / FocusFill #F2F5FA, Muted #BAC5D4, Faint #94A2B6, Accent #8AB8FF, AccentDeep #22395C, OnFocus #0A0F18, AccentOnLight #1C4F9C, Gold #FFCB57, GoldOnLight #9A6A00, Live #FF4D5E, Warning #FFCB80, WarningSurface #2E2414, Error #FF9C9C, ErrorSurface #331A1E, LogoPlate #223047.
Lane gradient pairs: France #27448F / #162550; Português #1C6B45 / #0F3527; Sports #8A4A16 / #45240D; Événements #6A2A6E / #2E1533. White and black are reserved for video overlays. Status always has text or a glyph.
`ErrorOnLight` #A52332 keeps unavailable-channel badges readable on the light focused row.

## 3. Typography
Android's system sans serif, using `Type`: Display 28/34 sp bold; Headline 22/28 semibold; Title 19/24 semibold; TitleSmall 17/22 semibold; Body 16/22; Label 15/19 semibold; Caption 14/18; Eyebrow 13/16 bold with 1.2 sp tracking; Badge 12/15 bold with 0.5 sp tracking. Running text stays at least 14 sp. Support 130% system font scale; metadata truncates with ellipsis, important labels wrap or grow.

## 4. Spacing & layout
`Spacing.Side` 58 dp, `Top` 28 dp, `Gutter` 20 dp, `Row` 28 dp. Drawer rail 80 dp and content inset 38 dp. Four Home columns share the actual available width after drawer and side margins; lane cards never scroll horizontally. Channel strips scroll independently with 10 dp vertical focus clearance. Browser list rows retain a 56 dp minimum; chips retain 32/38 dp minima, growing for scaled text. Screen-level footer gap 10 dp. Preview actions and metadata can wrap independently. List and category regions own their vertical scroll; Home owns vertical scroll and its channel strips own horizontal scroll.

## 5. Components
- `FocusSurface`: clickable row/button/chip, dark resting fill, light focused fill/dark content, subdued pressed fill, dim disabled state. Scale varies by density. Long-press semantics exist only for a real secondary action.
- `CardSurface`: artwork or lane card with white 3 dp focus outline, 1 dp resting outline, 1.05 scale. Channels show logo or initials, name, quality and category; favorites add a star.
- `TvButton`: 42 dp minimum, icon plus action verb, primary filled Accent, secondary outlined. `TvChip`: compact/sized variants; selected state includes a check and accessibility selection state.
- `TvTextField`: 48 dp minimum, scoped placeholder and accessibility label. Focus alone never opens the IME; OK edits; Back closes it and ends editing.
- `Badge`, `ChannelLogo`, `EventTag`: noninteractive metadata. A provider EPG ID is not a working programme guide and does not warrant a GUIDE badge.
- `HintBar`: nonfocusable remote key plus action label, contextual to browsing or playback.
- `EmptyState`, `Callout`, `NoticeHost`: empty/error explanations beside the affected content, with an available recovery action when applicable.
- `AppDrawer`: collapsed icon rail; focus expands it, OK navigates, Back on a drawer root opens the drawer. Focus restores when returning from playback.

## 6. Motion & interaction
Preserve existing native transitions: 150 ms surface scale; 160/120 ms screen crossfade; preview overlay 400 ms out / 150 ms in. Preview settles for 650 ms before opening a muted stream. Zapping settles for 500 ms before switching. Player banner hides after five seconds. Android animation duration settings govern Compose animation. No new decorative animation. OK opens/plays; long OK toggles favorite or pin; Right reaches preview actions; Left returns to the selected list row; Right/Down reach the next preview action, and Down from the final action returns to the row.

## 7. Depth & surface
Mixed tonal depth and focus elevation. Rows use 14 dp corners, cards/preview 16 dp, badges 6 dp, buttons/chips circular. Existing focus glow/shadow uses Accent with light focus outlines. Selection uses a tonal fill and check/preview glyph; focus must remain distinct from selection.

## 8. Accessibility constraints & existing limitations
All essential actions must be reachable with D-pad only. Preserve 58/28 dp TV safe margins, visible focus, 4.5:1 normal text / 3:1 large text contrast, password masking, and the HTTP transport warning. No account credentials on shared screens. Icon-only actions require accessibility names; selected chips expose selection state.
Existing limitations: provider logos/availability vary; event timezone is undocumented; physical-TV performance is unmeasured; programme coverage depends on the saved provider. Country flags remain existing navigational cues rather than introducing a new icon system. Setup with saved real credentials is not screenshotted in QA.

## TV guide extension
The guide uses a full-screen three-pane list-detail shell: channels (200 dp), programme list (remaining width), description/actions (260 dp), separated by Gutter. Each pane owns its vertical scroll; header and remote hints stay fixed. Existing Type/Palette/FocusSurface/TvButton tokens apply. Programme rows grow from 56 dp; titles allow two lines. At 130% text, details scroll independently and actions remain reachable. Initial focus is the selected channel; Right reaches its current/next programme, Left returns to that channel, Right from a programme reaches the direct-play action. Programme focus updates details without starting playback. OK on a programme or the direct action plays the live channel; future/past schedules never imply catch-up. Empty schedules, loading, and provider errors are distinct with refresh/retry. Provider epoch timestamps display in the TV's local timezone; schedule data loads on demand with a bounded 15-minute memory cache, not one request per catalog channel.

The navigation drawer owns a vertical scroll so Guide TV, Categories and Settings remain individually reachable at enlarged text sizes. During playback, Left opens the guide; Back restores playback. Description focus has a white outline and an explicit ↑↓ reading hint. Channel rows include version metadata to distinguish duplicate feeds.
