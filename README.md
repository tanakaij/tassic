# Tassic — Unified Life OS

A single-page **Progressive Web App built with Kotlin Compose Multiplatform (Kotlin/Wasm)**,
designed to be hosted on **GitHub Pages** and wrapped into a signed Android app
(**TWA via Bubblewrap**) — with full Adaptive Launcher Icons, offline support,
safe-area handling, and a fully **editable preset system** (nothing is read-only).

Design language extracted from `ui_template.jpg` (sky-blue canvas, white rounded cards,
deep-navy chrome, amber CTAs, serif display headings) with `Logo.png` powering the
splash screen, in-app header, PWA icons and the Android launcher.

---

## Modules

| Tab | What lives there |
|---|---|
| **Today** | CAGED shape-of-the-day + sub-task checklist, keyboard mode/key rotation, calisthenics logging with streaks, recovery "days clean" counters + one-tap relapse logging, priority to-dos |
| **Music Studio** | CAGED weekly schedule (Mon–Fri shapes, weekend song application), style trackers (fingerpicking / country / neo-soul), weekly song tracker, daily mode rotation (Ionian→Locrian), 12-key cycle, advanced modules (preacher chords, tritones, rootless voicings, 9/11/13 chords), monthly gospel album goals, custom instruments |
| **Life & Goals** | Things-3-style standalone goals (short/medium/long horizon, progress, target dates), purchases & wishlist (priority, target price, links, purchased toggle), GeoDev roadmap (5 seeded stages with free resource links, multi-path, portfolio build log → journal) |
| **Faith** | Recurring routines (Daily Bible Reading, Thursday Fasting, Praying Mountain trips) with Web Notification triggers, prayer points manager with answered-prayer timestamps |
| **Journal** | Day-One-style multimodal entries (rich text with `#`/`-` formatting, 1–5 mood, **voice notes via MediaRecorder → IndexedDB**), recovery history with trigger reflection log |
| **Insights** | Momentum score, 28-day trend + contribution heatmap, per-domain balance bars, ranked observations with the evidence behind each, and a generated week-in-review |
| **Settings** | Theme/accent/motion, reminder rules, quiet hours, digest schedule, home-surface toggles, and a delivery-diagnostics panel |

---

## v2 — intelligence, delivery and the design pass

### The intelligence layer

`data/Insights.kt` is a pure, dependency-free analytics engine over the store.
Nothing in it is invented: every observation names the rows that produced it.

- **Momentum score (0–100)** per domain — practice, fitness, tasks, faith,
  recovery — each an explainable ratio of what happened to what was scheduled,
  with a week-over-week delta.
- **Pattern detection**: recovery resets clustered by weekday and 6-hour block;
  goal pace vs. elapsed time with the required weekly rate to catch up; task
  inflow vs. outflow; mood trend and the mood-on-training-days association
  (stated as an association in the user's own log, not a causal claim);
  most-productive hour window; quietest weekday; album completion projected from
  observed learning rate.
- **Ranked next actions** blending urgency with time of day, so an evening open
  surfaces different work from a morning one.
- **Generated week in review**, reused as the body of the weekly notification.

This required a new `ActivityLog` table. The previous schema only stored
*current* state — `doneEpochDay` is the last time something was ticked, not a
history — so nothing could answer "how did this week compare to last week".
Every completion path now writes one small row, capped at 4,000 entries.
`TodoItem` also gained `completedAt`, without which no completion-rate analytics
were possible at all.

### Why notifications weren't working, and what changed

Four separate causes, all fixed:

1. **Reminders only ran while a specific tab was open.** The faith-routine poll
   lived inside `FaithTab`, so a routine reminder could only fire while you were
   sitting on the Faith tab. There is now one app-wide scheduler in `App.kt`.
2. **Nothing could fire while the app was closed.** A page timer dies with the
   page. The app now hands sw.js a rolling 7-day schedule, and the worker
   delivers on *every* wake it gets — periodic sync, one-off sync, push, a
   notification click, or any navigation. A backend-less PWA cannot be woken at
   an exact minute by anything, but this is the difference between "sometimes"
   and "never".
3. **Notifications had no actions and no follow-through.** Reminders now carry
   *Mark done* and *Snooze*. Because a worker can't write localStorage and
   Compose can't read the Cache API synchronously, the worker parks actions in a
   cache entry and a bridge in `index.html` moves them into localStorage, where
   the store replays them on the next tick.
4. **Failures were invisible.** Settings → Diagnostics now names the exact
   cause: permission state, whether a worker is controlling the page, whether
   the app is installed, and whether periodic background sync exists here.

Added alongside: quiet hours that *hold* rather than drop suppressed reminders,
a morning brief, an evening streak-at-risk nudge, and a weekly review.

### Why the widget wasn't appearing

The Widgets API (`self.widgets`) only has a host on Windows, via the Edge
widgets board. On Android there is no PWA home-screen widget to install, so no
amount of manifest work would have produced one. The lifecycle handlers are
fixed and the payload is now real rather than a static placeholder, but the
honest Android answers are the two surfaces a PWA *can* own, both now
implemented:

- **Badging API** — the outstanding count on the installed app icon.
- **Pinned "Today at a glance" notification** — silent and sticky, carrying
  momentum, what's due and the next action. This is the practical widget
  substitute, and it's an opt-in toggle.

App shortcuts were expanded to five (Today, Insights, Journal, Music, Settings).

### Design pass

- **Semantic token layer** (`TassicTokens`) replaces raw palette constants.
  Components previously referenced `CardWhite` and `Navy` directly, so dark mode
  only worked where someone had remembered to branch on it — cards stayed pure
  white on a navy canvas. Everything now asks for "the card colour".
- **Bottom navigation.** A drawer alone is the wrong primary navigation for a
  multi-section mobile app: every section change cost a swipe plus a tap, and
  nothing on screen indicated where you were.
- **New surfaces** — hero, glass, ink, sunken — so a headline stat and a
  checkbox row no longer carry identical visual weight.
- **Charts**, all Canvas-drawn with no dependencies: progress ring, sparkline,
  bar rows, contribution heatmap.
- **Segmented control** replacing FilterChips used as a view switcher (chips
  read as multi-select filters; these are one-of-N).
- Manual light/dark override, five accent colours, and a reduce-motion setting
  that parks the ambient wallpaper instead of removing the depth.
- Refined type scale with proper tracking, contextual header with a time-of-day
  greeting, press-scale feedback, count-up numbers, and a rebuilt splash.

### Other gaps closed

- **Recurring tasks** (`DAILY` / `WEEKDAYS` / `WEEKLY` / `FORTNIGHTLY` /
  `MONTHLY`). Completing one rolls the due date forward and re-arms the reminder
  instead of retiring it — without this a task list can't hold real routines.
- **Effort estimates** on tasks, feeding a "today's load" figure.
- **Snooze** with a configurable interval.
- Per-day notification bookkeeping keys are now pruned; they previously
  accumulated in localStorage forever.

---

**Every seeded preset is ordinary editable data**: rename, re-schedule, retarget,
reorder or delete anything; add custom habits, shapes, styles, modules, exercises,
stages, routines, paths, items and entries via FABs and modal bottom sheets.

---

## Project layout

```
Tassic/
├── .github/workflows/deploy.yml      # Pages deploy + Bubblewrap APK/AAB
├── twa-manifest.json                 # Bubblewrap config (__OWNER__ templated in CI)
├── settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
├── tools/generate_icons.sh           # logo.png → PWA icon set (sips)
└── composeApp/
    ├── build.gradle.kts              # kotlin("multiplatform") → wasmJs + SQLDelight
    ├── webpack.config.d/webpack.js   # relative publicPath for Pages sub-paths
    └── src/
        ├── commonMain/sqldelight/tassic/db/   # 14-table canonical schema (.sq)
        └── wasmJsMain/
            ├── kotlin/tassic/
            │   ├── Main.kt               # ComposeViewport entrypoint
            │   ├── platform/             # Browser, AudioRecorder, AudioStore, Notifications/Badge/Widgets
            │   ├── data/                 # Models, Settings, Seeds, Store, Time, Insights, Reminders
            │   └── ui/                   # App shell, theme, components, charts, sheets, 7 tabs
            └── resources/                # index.html, styles.css, manifest.json, sw.js, icons/
```

## Stack & versions

| Component | Version |
|---|---|
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.9.3 (Material 3, `ComposeViewport`) |
| kotlinx-coroutines / serialization | 1.10.2 / 1.9.0 |
| kotlinx-browser | 0.5.0 |
| SQLDelight (plugin + runtime) | 2.3.2 |
| Gradle | 8.14.3 |

### Persistence architecture

The **canonical relational schema** lives in
`composeApp/src/commonMain/sqldelight/tassic/db/*.sq` (`goals`, `todo_items`,
`music_practice`, `album_goals`, `recovery_tracker`, `habit_logs`, `workout_items`,
`workout_logs`, `career_items`, `journal_entries`, `prayer_points`, `faith_routines`,
`wishlist_items`, `app_meta`) and is **compile-time verified** by the SQLDelight plugin.

At runtime on the web, `tassic.data.Store` persists the same typed rows to
**localStorage** (JSON, synchronous, offline-safe) with audio clips offloaded to
**IndexedDB** — this avoids the WebWorkerDriver's SharedArrayBuffer/COOP-COEP
requirement, which GitHub Pages cannot satisfy. Swapping to the generated
`TassicDatabase` + `WebWorkerDriver` later only means re-pointing the CRUD methods
in `Store.kt`; no UI code changes.

## Local development

Requires JDK 17+ and Gradle (or run `gradle wrapper` once to materialize the wrapper jar).

```bash
gradle :composeApp:wasmJsBrowserDevelopmentRun   # dev server on :8080
gradle :composeApp:wasmJsBrowserDistribution     # production bundle
# → composeApp/build/dist/wasmJs/productionExecutable
```

Regenerate icons after changing the logo:

```bash
./tools/generate_icons.sh Logo.png
```

## Deploy (GitHub Pages + Android TWA)

1. Push to GitHub; enable **Settings → Pages → Source: GitHub Actions**.
2. The pipeline:
   - `wasmJsBrowserDistribution` → deploys to **gh-pages** environment.
   - Rewrites `__OWNER__`/`/Tassic/` in `twa-manifest.json` to your repo URL.
   - Installs `@bubblewrap/cli`, restores/creates the signing keystore and emits
     signed **`.apk` + `.aab`** (Bubblewrap generates Adaptive Launcher Icons for
     `mipmap-hdpi/xhdpi/xxhdpi/xxxhdpi` from the 512px + maskable icons, so
     `Logo.png` appears correctly in the app drawer, task switcher and notificaions).
3. Optional secrets for stable signing:
   - `ANDROID_KEYSTORE_B64` (base64 of your `.jks`), `KEYSTORE_PASSWORD`, `KEY_PASSWORD`,
     `BUBBLEWRAP_KEYCHAIN_PASSWORD`.
   - If absent, CI generates an ephemeral keystore (fine for testing, **not** for Play Store).

## Safe areas & system UI

- `index.html` uses `viewport-fit=cover`; `styles.css` pads the Compose host with
  `env(safe-area-inset-*)` so content clears the notch and gesture bar.
- Compose additionally applies `statusBarsPadding` (header), `navigationBarsPadding`
  (bottom nav) and `displayCutout` insets, plus `imePadding()` inside every modal
  sheet — no-ops on web, correct if the UI is ever shared to an Android target.
