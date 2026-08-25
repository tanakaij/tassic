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
            │   ├── platform/             # Browser, AudioRecorder, AudioStore, Notifications
            │   ├── data/                 # Models, Seeds (editable presets), Store, Time
            │   └── ui/                   # App shell, theme, components, sheets, 5 tabs
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
