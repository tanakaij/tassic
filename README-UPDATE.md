# Tassic v3.6 — everything from this session, in one drop

This archive contains **only the files that changed or are new**. Copy the
`composeApp/` tree over your existing checkout, keeping the folder structure,
and rebuild:

```bash
gradle :composeApp:wasmJsBrowserDevelopmentRun     # dev server on :8080
gradle :composeApp:wasmJsBrowserDistribution       # production bundle
gradle :composeApp:wasmJsBrowserTest               # the new test suite
```

No dependency or schema changes. Existing localStorage data loads unchanged —
every new model field has a default and the store already ignores unknown keys.

---

## About the navigation question you asked

**Yes — a drawer and a bottom bar together is correct, as long as they do
different jobs.** The mistake was having both hold the same list: seven
identical rows in the drawer, five of the same in the bar, no reason to prefer
either. They now split by purpose:

- **Bottom bar** — the four places you move between constantly (Today, Plan,
  Journal, Insights) with a raised **capture** button in the middle. The middle
  of the bar is the easiest place on a phone to reach, and putting capture there
  makes it available from every screen.
- **Drawer** — the full map, grouped into *Daily*, *Long game* and *System*,
  plus a live momentum readout and three quick actions. Sections you switch off
  in Modules disappear from it entirely.
- **Header** — search replaced the settings cog. Search is needed constantly;
  settings are visited monthly.

---

## Part one — the companion release

### Quick capture that reads plain English — `data/Nlp.kt`

One line becomes a fully-formed row:

```
gym tomorrow 7am every weekday ~45m !high #health
→ Task · tomorrow 07:00 · repeats weekdays · 45m · High · #health

buy audio interface $149 !high     → wishlist item, priced
review notes for 45 minutes        → task with a 45-minute estimate
note: E-shape run works better…    → journal note
```

Dates (`friday`, `next tuesday`, `in 3 days`, `12 sep`, `12/09`), times (`7pm`,
`19:00`, `noon`), repeats, priority, estimates, tags, prices and reminders.
Chips show what was understood **before** you save, and anything unrecognised
**stays in the title** rather than being silently dropped.

### The Plan tab — `ui/tabs/PlanTab.kt`, `data/Agenda.kt`

One timeline assembling tasks, habits, rhythms, training, practice and now
calendar events. Because it knows durations it can detect **clashes**, judge
**whether the day fits** ("1h 40m left against 3h free; longest clear run starts
at 14:00" — or honestly, "that's 50m more than today holds"), and suggest
**where the loose work could go**. Week view shows load and closure per day.

### Habits, search, review, focus, backup

Cadences including *N times a week*, counted targets, streaks counted over the
days a habit was actually **due**, and a four-week trace next to each one.
Global search across every table. An evening review generated from your log.
Focus sessions that survive a reload. And export/import/erase, which mattered
most of all — everything lives in localStorage, which a browser can evict.

---

## Part two — the seven you asked for

### 1. Calendar import — `data/Ics.kt`

Handles line unfolding, DATE and DATE-TIME stamps, UTC conversion, DURATION, and
the RRULE parts that actually occur (FREQ, INTERVAL, BYDAY, UNTIL, COUNT).
Recurrence is *evaluated*, not expanded, so an open-ended daily event doesn't
generate an unbounded table.

Imported events are **read-only** on the plan — Tassic isn't the system of
record for someone else's invite, and a checkbox that silently refuses to tick
is worse than none. They block time but don't count as your workload, so a day
of back-to-back meetings doesn't read as an impossible to-do list.

File import is offered first and URL second, because most providers send no CORS
headers and a pasted URL will simply be refused. **No proxy was added** —
routing your private calendar through a third party to dodge a browser security
control is a worse trade than the limitation.

### 2. A test source set — `composeApp/src/wasmJsTest/`

`wasmJsTest` with headless Chrome, and three suites covering the pure logic:

- **`NlpTest`** — 13 tests over the capture grammar, including the regression
  that motivated the whole thing (punctuation trimming ate the `!` off `!high`
  and silently disabled the priority grammar), plus the trust-critical property
  that unrecognised text is never dropped.
- **`IcsTest`** — folded lines, all-day events that must not draw a 24-hour
  block, BYDAY, INTERVAL, UNTIL, escaped text, and non-calendar input.
- **`PlanningTest`** — clashes, unusable free gaps, impossible days, placement
  suggestions that never overlap a booking, habit streaks over due days,
  weekday habits surviving the weekend, goal auto-progress and the rule that a
  hand-set goal is never overwritten, plus month arithmetic across a year
  boundary and a leap February.

I verified every asserted value against an independent reference implementation
before writing them, since I can't run Gradle here.

### 3. The weekly ritual — `WeekPlan`, `WeeklyPlanSheet`

Three priorities, hard-capped. A weekly plan with ten items is a backlog wearing
a plan's clothes; the value of the ritual is being forced to say what matters
*most*, which only happens when the list is too short to dodge the question.
Unfinished priorities from last week are offered as one-tap carry-overs.
`Coach.weekVerdict` then measures the week against those rather than against how
busy it looked.

### 4. People — `data/People.kt`, `ui/tabs/PeopleTab.kt`

Birthdays, a preferred contact rhythm, and a plain statement of how long it's
been. Birthdays appear on the day plan; overdue contacts surface in Insights.

The tone was the hard part. Nobody wants software scoring their friendships, so
there are **no streaks and no grades** — it says "seven weeks since you last
spoke" and stops. Logging a conversation also counts toward momentum, because
that is at least as valid a use of a day as closing a ticket.

### 5. The lock — `data/Lock.kt`, `ui/components/LockGate.kt`

A PIN over Journal, Recovery and People — chosen per section, not over the whole
app, which would add friction to every use and get switched off within a week.
Salted, iterated hash, so the PIN itself is never stored.

**The UI states plainly that this is a screen and not encryption.** Your entries
remain readable JSON in browser storage regardless of the PIN. Claiming more
would be the comfortable lie and the dangerous one, because it would change what
you're willing to write in there.

### 6. Modules and first run — `ui/components/Onboarding.kt`

A first-launch picker that also decides what gets **seeded**, so an unchosen
module doesn't leave preset rows waiting in storage. Everyone previously
received CAGED shapes, Thursday fasting and a recovery counter whether or not
any of it applied — which is the fastest way to make an app feel like it was
built for someone else. Toggles hide sections without deleting anything.

### 7. Photos in the journal — `platform/MediaStore.kt`

Picker → canvas downscale to 1600px → JPEG at 0.72 → IndexedDB. The downscale
isn't optional: thirty full-resolution phone photos would push the origin over
quota and start getting evicted, taking your voice notes with them. One photo
per entry, deleted with the entry, with a new Photo filter on the journal.

---

## Part three — growth and good deeds

Everything else in Tassic counts throughput: tasks closed, sessions logged, days
clean. A life can score extremely well on all of that and still be going nowhere
in particular, because none of it touches what kind of person you're becoming.

**`data/Growth.kt`, `ui/tabs/GrowthTab.kt`** — and this tab deliberately breaks
the app's own rules.

**Areas.** Name something you want to be better at across eight dimensions —
character, mind, body, spirit, relationships, craft, money, service. Each one
holds an intention in your own words, the *concrete practices* you've chosen
("let someone else finish before I speak", not "be kinder"), and the **evidence
line**: what honest progress would look like, written while you're thinking
clearly.

**A monthly rating, not a daily tick.** Character doesn't move on a daily scale,
and asking someone to score their humility every evening produces noise and a
habit of constant self-grading, which for most people is not growth — it's a
nicer-looking form of self-criticism. Five points, with the middle marked **"about
the same"**, because for most things in most months that is the true answer, and
an interface that makes honesty feel like failure will stop being used honestly.
The review shows your evidence line **before** the picker, so the standard is the
one you set, not your mood on the 29th.

Nothing is said about direction until three months are rated. Two points is a
mood, not a trend, and telling someone their patience is declining on the
strength of one bad month is both wrong and unkind.

**One good thing a month.** Logged after the fact, never scheduled — a good deed
added to a to-do list in advance becomes an errand. Recipient optional, because
a fair amount of the best of it is the kind nobody signs. Linking a person you
already track also marks them as spoken to.

**And there is no streak on it, by design.** The counter says *how many of the
last twelve months contain something*, never how many in a row. A streak turns a
kindness into a number you're protecting, and something done to keep a count
alive is a different act from the one this is trying to build. A missed month
leaves a gap and nothing more — there is deliberately nothing here to break.

---

## Part four — the Faith tab, and the logos

### You asked whether anything I'd built made you a better Christian. It didn't.

The honest answer when you asked was no. I'd added a SPIRIT dimension to the
Growth tab and a good-deeds log, but I never opened the Faith tab — it still did
exactly what it did before: rhythms, reminders, and a list of prayer requests.
That's a decent scaffold and it helps with none of what actually makes up an
ordinary Christian week. So:

**Reading plans — `data/Bible.kt`.** The structure of the canon is plain factual
data — sixty-six books and their chapter counts, 1,189 in total — so plans are
*generated on your device* rather than downloaded. Seven templates: the Gospels
in forty days, the New Testament in ninety, Psalms with a proverb alongside, a
proverb a day, John in three weeks, the whole Bible in a year or in six months.
Chapters distribute evenly with the remainder spread across the earliest days,
because a plan that ends with a nine-chapter day is a plan people abandon on day
364. Progress is a set of completed days, not a pointer, so a skipped day can be
picked up later instead of forcing you to lie or lose it. Being behind is stated
plainly and without weight — a reading plan that shames you on day nine is one
you delete on day ten.

**Verse memory.** Leitner boxes at one, three, seven, twenty-one and sixty days.
The reference shows first and the text stays hidden until you say you've had a
go, because a card that shows the answer alongside the prompt teaches
recognition, which feels like knowing it and isn't. A miss drops straight back
to box one — a verse you couldn't recall isn't slightly less learned, it's
unlearned.

**Guided prayer.** A four-movement session on the old ACTS shape — adoration,
confession, thanksgiving, supplication — which exists precisely to stop prayer
collapsing into a list of things you want. Your own prayer list surfaces at the
supplication movement with a tick that records only *that* it was prayed over.
Prayer points now carry how long you've held them and how many times you've
prayed them, which is what makes an answer, when it comes, land as more than a
checkbox. **The prompts are questions, never words to pray.** An app writing
someone's prayers for them would be presumptuous and useless in the same breath.

**Gratitude.** Three lines a day, capped at three. The exercise is choosing; a
list of fifteen is a weaker act than naming the three that stood out.

**Two things this deliberately does not do.** It ships **no scripture text** —
every modern translation is under copyright, and bundling a megabyte of KJV to
duplicate what YouVersion and Bible Gateway already do better is a poor trade,
so Tassic holds references and hands off to your reader. And it makes **no
theological claims of its own**. Everything it says back to you is drawn from
what you recorded. It is a set of tools for a practice you already have, not a
devotional writing at you — and I'd rather be plain that I'm not the right thing
to be forming anyone's faith.

### The logos

The old mark was a 480px raster that softened on any modern screen, and the
splash floated it on a translucent white square that read as a placeholder.

`resources/mkicons` output is now generated from exact coordinates at 4× supersample:

```
logo.png                  1024, transparent — splash and in-app header
icons/icon-512.png        rounded navy tile, gradient ground, soft bloom
icons/icon-192.png        same, launcher size
icons/apple-touch-icon.png  180, linked properly for iOS
icons/favicon.png         64
icons/maskable-512.png    full bleed, mark inside the 80% safe circle so
                          launchers can crop to circle/squircle/teardrop
icons/monochrome-512.png  solid silhouette for Android themed icons
```

The identity is unchanged — the same folded T, the dark wings under the fold,
the amber accent showing through the throat — but rebuilt as geometry so it
stays crisp at every size. The stem uses a lighter navy than the original,
because on a navy tile the near-black original disappeared into the background.

The splash now uses the same navy tile, bloom and amber the launcher icon and
in-app chrome use, with a slow specular sweep across the mark and a dark-mode
variant. Launching no longer feels like passing through a different product on
the way in. All of it is disabled under `prefers-reduced-motion`.

---

## Part five — closing the gaps I left

When you asked whether every aspect was now enhanced, the honest answer was no.
This pass fixes what I'd actually left broken.

### Controls that did nothing

- **Habit reminders.** `Habit.reminderOn` and `reminderHour` were editable from
  the first build and read by no code at all, so setting one produced an alert
  that could never arrive. `Reminders.tick` now fires them — skipping habits
  already kept today, respecting quiet hours, and leading with the streak at
  risk, because "a 6-day run still open" is worth more at 8pm than "not done".
- **Memory verses** now notify too, batched into one alert rather than one per
  card. Five separate notifications for five due verses is how people turn
  reminders off entirely.
- **`habitsOnToday`** now actually gates the Today segment. It previously did
  nothing.
- **`focusBreakMinutes`** now has a break phase to configure. Break time is
  deliberately *not* logged as focus — it isn't, and counting it would make the
  hour-of-day analysis describe rest as work.
- The app badge and evening nudge both count habits now, since habits became
  first-class daily work three parts ago and neither had noticed.

### Intelligence that existed but was unreachable

`Coach.peopleInsights` was written and never called from anywhere. Habit and
growth insights only appeared inside their own tabs. The Insights tab — the one
place a person would go looking for synthesis — knew nothing about habits,
people, growth, focus or the calendar.

The newer engines are now merged into the Signals list and sorted on the same
weight scale as the original, and a new **Rhythm** segment carries habit
consistency bars, focus minutes, your productive window, overdue contacts,
upcoming birthdays and monthly growth ratings. I did not fold four domains into
the 1200-line original analyser; merging at the screen keeps both engines
readable.

### Music Studio

Six lists and no sense of whether practice was happening. Now: a studio header
with practice streak, minutes this week and songs against target; **timed
practice sessions** that write real minutes to the log instead of asking you to
estimate afterwards; and `Coach.musicInsights`, which names the shape, mode or
key that has gone cold. That last one matters most — comfortable material
rehearses itself, and nothing in the app previously surfaced the rest.

### Life & Goals

The list was fine; what it never did was notice when a goal went quiet. A goal
untouched for six weeks sat at 40% looking identical to one that was moving, and
the page was becoming a museum. `Coach.goalInsights` now flags stale goals
(quoting your own stated motivation back at you), deadlines closing in under
70%, dates already passed, and a horizon imbalance — all-short tends to be
drift, all-long tends to be avoidance. Goals show a horizon breakdown; the
wishlist shows outstanding and spent totals.

### Still not done, so you know

*(Both of these were closed in part six below.)* None of this has compiled —
same reason as before.

---

## Part six — the last two gaps, and a verification sweep

### The widget and the worker

Both were listed as "still not done" last time. Both are done.

**The service worker needed no changes at all** — `sw.js` is generic over a
reminder's `kind`, so it fires whatever the schedule hands it. The gap was
upstream: `reminderScheduleJson` only emitted tasks and routines, so a habit
reminder could only arrive while Tassic happened to be open, which is precisely
when you don't need reminding. Habits and memory verses are now in the seven-day
schedule the worker receives, and `drainNotificationActions` handles a habit's
"Mark done" tap so a habit ticked from the notification shade doesn't get
re-reminded on next launch.

**The widget** was showing `modulesDone` — a music-practice count, labelled
"modules done", meaningless to anyone not using the studio — while habits, the
thing most people open the app for, were absent from the home screen entirely.
That tile now reads habits kept, and the payload additionally carries today's
reading, verses due and whether this month has a good deed logged.

### The sweep, and what it found

I wrote a checker that walks every file for brace/paren balance with a
line-accurate depth trace, duplicate imports, unresolved `tassic.*` imports,
navigation targets that don't name a real `Tab`, JSON validity, and whether
every `${key}` the widget template binds is actually emitted by the app.

**It found a real bug I'd just introduced.** My schedule edit landed *outside*
the `if (s.remindersOn)` guard, so its trailing brace closed the `Store` class
about 120 lines early — everything after it would have failed to compile. The
naive "count the braces" check I'd been running all session reported this as a
single `-1` and couldn't say where; the depth trace pointed at the exact line.
Fixed, and the checker now runs over the whole tree rather than as a
one-off.

### Navigation

Three dead ends, all from the module toggles I added in part two:

- **Switching off the module for the section you're currently in** left the app
  rendering a screen that had vanished from the drawer and the bottom bar, with
  no way to leave it. It now falls back to Today.
- **Search and the command palette** could navigate into a disabled section by
  name, including via a `?tab=` deep link. Both now check the module first.
- **Plan's Habits segment** and **Today's segments** ignored their toggles. Plan
  drops the segment; Today already filtered its list but captured the initial
  selection once, so turning a module off later left the switcher pointing at a
  segment that no longer existed and the page rendered blank.

All ten `Tab` names, and every `actionTab` string in every insight across four
engines, now resolve to a real destination — verified mechanically rather than
by eye.

---

## Part seven — dark mode, and how fast it opens

### The theming bug

`Navy` (#0F2B4C), `Ink` (#123252), `Muted` (#5B7A99) and `SkySoft` (#D8EAF6)
are fixed constants chosen when the app had only a light theme. Five screens —
Faith, Journal, Life, Music and Today — still used them for body text after dark
mode arrived, at 23, 14, 17, 25 and 14 references against zero uses of
`LocalTokens`. In the dark theme those tabs rendered near-black text on a
near-black background, and none of them responded to the accent picker at all.

I made it worse before I found it: the goal-health and studio cards I added a
part ago used `Navy` and `Muted` too, so I extended the bug into new surfaces
while reporting those tabs as fixed.

`ui/theme/ThemedColors.kt` adds composable property getters — `textInk`,
`textMuted`, `surfaceSoft`, `ruleSoft` — that read the active tokens at the
point of use. That made the fix a one-for-one substitution at 66 call sites
rather than threading `LocalTokens.current` through a few dozen private
composables. The constants stay: they're still correct as *brand* colours, and
the theme is built from them. What was wrong was using a fixed brand colour
where a semantic role was meant.

One case was left alone deliberately — the roadmap stage badge is a solid dark
chip with white text in both themes, so it now takes the `chrome` token rather
than a text colour.

### Cold start

**Navigation was network-first with a three second deadline.** Every cold launch
— including offline ones, and including installed-app launches on a bad
connection — sat waiting on the network before it would serve a shell already
sitting on disk. For a fixed HTML shell wrapping a Wasm bundle that buys
nothing: the document almost never changes, and taking an update on the next
launch is the standard trade for an app that has to open instantly.

It is now cache-first with background revalidation, so an installed launch
paints from disk immediately and quietly refreshes the shell for next time.

Alongside that:

- **Navigation preload** is enabled, so the browser starts the network request
  in parallel with booting the worker instead of after it.
- **`skiko.js` and `composeApp.js` are preloaded** from `<head>` and marked
  `fetchpriority="high"`. The browser previously didn't discover either until it
  reached the bottom of the body — most of a second of doing nothing on a cold
  start.
- The precache shell picked up the two new icons, and the cache version was
  bumped to `v5` so existing installs actually take the new shell.

`sw.js` passes `node --check`, and the sweep now verifies that every file the
worker tries to precache actually exists in the resource tree — a 404 there used
to be survivable but silent.

### What "offline" now means, precisely

After one successful online visit: the shell, styles, icons, widget files and
the Compose bundle are all cached, so the app opens and runs with no network.
Reminders continue to fire from the worker's seven-day schedule. Nothing syncs,
because there is nothing to sync to — see the note on backups above.

---

## Part eight — Today as a hub

### A correction first

I told you Today had "no status card at all". That was wrong — I looked at the
wrong 75 lines of the file. Today already had a hero: greeting, the day's
headline, a momentum ring, and a "START WITH" row carrying the top next action,
its reason and a sparkline. It's a good card.

What it could not do was see past tasks, training and practice, because the
engine behind it predates habits, the calendar, reading plans, people, week
priorities and good deeds. So Today described about a third of your day with
total confidence, and everything that could have completed the picture had been
built one tab over in Plan.

### What was added

A single compact strip under the existing hero, and nothing else.

`Coach.signals()` walks the domains the hero's engine doesn't know about and
returns **at most three** rows, chosen by what's actually true right now:

1. the next thing pinned to a clock, from tasks or an imported calendar — the
   only item on the list that can be missed simply by not looking up
2. the day not fitting, with the overage named
3. a habit streak about to break (after 4pm, where it's actionable) — otherwise
   a plain kept/due count
4. today's reading, then verses due
5. a birthday today or tomorrow, which jumps the queue because it is the one
   thing here that can't be done late
6. the first open priority from your week plan
7. no good deed logged, but only in the last week of the month — before that
   it's a nag rather than information

Each row is one tap to the tab that owns it. The card renders nothing at all
when none apply, which on a clear day is the correct output.

### Why not a second hero

Because that's how a home screen becomes a dashboard. Eleven tracked domains
could each justify a tile, and eleven tiles is impressive on the first morning
and ignored by the second — worst of all on a morning you're already behind,
which is exactly when the screen needs to be readable. The hero answers *how am
I doing*; this answers *is there anything I'd otherwise miss*; the checklists
below answer *what do I do*. Three questions, three sizes, in that order.

Plan keeps its Day view. The two screens now have genuinely distinct jobs —
Today is status, Plan is schedule — rather than competing for the same one.

---

## Files in this drop

**New**

```
data/Nlp.kt                natural-language capture grammar
data/Agenda.kt             day plan, clashes, free slots, week summaries
data/Coach.kt              habit analytics, schedule advice, reviews, people
data/Search.kt             cross-table search index
data/Ics.kt                iCalendar reader
data/People.kt             keep-in-touch logic
data/Lock.kt               PIN hashing and session state
data/Growth.kt             growth areas, monthly ratings, good deeds
data/Bible.kt              canon structure + reading plan generation
platform/Files.kt          download / pick / share / haptics / chime / fetch
platform/MediaStore.kt     downscaling image picker + IndexedDB
ui/components/QuickCapture.kt    capture sheet with live parse preview
ui/components/CommandPalette.kt  search + actions overlay
ui/components/HabitUi.kt         habit row, trace, editor
ui/components/FocusTimer.kt      focus session sheet
ui/components/ReviewSheet.kt     evening review + check-in
ui/components/Feedback.kt        undo-capable snackbar
ui/components/LockGate.kt        PIN gate and keypad
ui/components/PeopleUi.kt        person sheet, weekly plan sheet + card
ui/components/Onboarding.kt      module picker + StoredImage
ui/components/GrowthUi.kt        growth sheets, rating picker, deed sheet
ui/components/FaithUi.kt         reading plan, verse memory, gratitude, prayer
ui/tabs/PlanTab.kt               day / week / habits
ui/tabs/PeopleTab.kt             contacts and birthdays
ui/tabs/GrowthTab.kt             areas and good deeds
src/wasmJsTest/…/NlpTest.kt, IcsTest.kt, PlanningTest.kt
```

**Updated**

```
composeApp/build.gradle.kts   test source set + karma
data/Models.kt      + Habit, SubTask, Person, CalendarFeed/Event, WeekPlan,
                      GrowthArea, GrowthCheckin, GoodDeed; journal photos
data/Settings.kt    + companion, capture, focus, weekly, lock, modules, calendar
data/Store.kt       + eight tables, habit/people/calendar/growth APIs,
                      goal auto-progress, export/import/erase, undo-safe restore
data/Seeds.kt       + starter habits and growth areas
ui/App.kt           new shell, People + Growth tabs, onboarding, prompts, modules
ui/tabs/TodayTab.kt      + Habits segment, module-aware views, recovery lock
ui/tabs/JournalTab.kt    + photos, Photo filter, recovery lock
ui/tabs/SettingsTab.kt   + Companion, Privacy, Modules, Data and Calendar
ui/components/JournalSheets.kt  + photo attachment
ui/tabs/FaithTab.kt      + Reading, Word and guided prayer segments
data/Reminders.kt        + habit and verse reminders, habit-aware badge/nudge
data/Coach.kt            + goal health, practice streaks, music insights
ui/tabs/InsightsTab.kt   + Rhythm segment, merged habit/people/growth engines
ui/tabs/MusicTab.kt      + studio header, timed practice, cold-material insight
ui/tabs/LifeTab.kt       + goal health, horizon balance, wishlist totals
ui/components/FocusTimer.kt  + break phase
data/Store.kt            + habit/verse worker schedule, habit notification
                           actions, habits/reading/deeds in the widget payload
ui/App.kt                + module-aware navigation fallbacks
resources/widgets/*      second tile repointed from practice modules to habits
ui/theme/ThemedColors.kt (new)      theme-aware text and surface accessors
ui/components/TodaySignals.kt (new) the cross-domain strip under Today's hero
data/Coach.kt                       + Coach.signals(), capped at three
ui/tabs/{Faith,Journal,Life,Music,Today}Tab.kt  static colours -> tokens
resources/sw.js          cache-first navigation, preload, v5 shell
resources/index.html     engine preload + fetchpriority
resources/index.html     icon links
resources/styles.css     premium splash
resources/manifest.json  new app shortcuts + full icon set
resources/logo.png, icons/*  regenerated mark and app-list tiles
```

---

## What I verified, and what I couldn't

**Verified.** All 35 files balance on braces, parens and brackets. Every
`tassic.*` import resolves to a real declaration in either this drop or your
existing checkout. Every value asserted in the tests — parser outputs, weekday
alignment, free-slot arithmetic, month indices, leap-February day counts — was
checked against an independent reference implementation.

**Not verified.** I could not compile. This environment has no Gradle and the
Maven repositories aren't reachable, so I stayed strictly inside the Compose
APIs your codebase already uses and checked cross-file calls by hand. Expect to
fix an import or a signature on first build; the logic underneath has been
checked far more carefully than the wiring.

---

## What was deliberately not added

No cloud sync, no account, no AI service call, no CORS proxy, no streak on the
good deeds. Every number in the app is derived from rows you created, on your
device, and every observation names the evidence behind it. The moment that
stops being true, the insights stop being worth reading — and an insight you
can't trust is worse than none at all.
