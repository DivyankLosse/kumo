# Kumo: v1.0.0 release review and v1.0.1 plan

Written 2026-11-26 against commit `a8e457a`. The app was renamed from "ASCEND 2027" to **Kumo** before this review; everything below uses the new name.

---

## 1. v1.0.0 release readiness

| Check | Result |
|---|---|
| Hardcoded keys/secrets (full git history) | None found. The AI Coach talks only to a user-configured proxy, and the APK holds no model keys. `font_certs.xml` holds Google's public Play-services font certificates, which are not secrets. |
| Personal data in the repo | None. The seed profile is generic ("Ascender", 173 cm, etc.). Your name, email and phone appear nowhere. |
| Commit identity | Rewritten before v1.0.0 to the GitHub noreply address, so commits are credited to the profile and no personal email is exposed. |
| Unfinished stubs | No TODO, FIXME or `NotImplemented` markers. The Coach backend doesn't exist yet, but the client says "no reply" honestly instead of faking one, so this is not a stub. |
| Build | Debug builds locally and in CI: GitHub Actions runs on `main`, both runs so far are green, and each uploads the `kumo-debug-apk` artifact. The R8-minified release build compiles but is **unsigned** and has never been run. |
| Tests | 24 JVM tests + 18 instrumented tests, all passing on an API 35 emulator. |
| License | MIT (`LICENSE`), added for v1.0.0. |
| Docs | README covers build steps, schema, the Rive pipeline and known limitations. |

### 1.1 Publishing v1.0.0 (done 2026-11-26; kept for reference)
```bash
# 1. pick and add a license (MIT shown), commit
gh api /licenses/mit -q .body | sed "s/\[year\]/2026/; s/\[fullname\]/Divyank/" > LICENSE
git add LICENSE && git commit -m "Add MIT license"

# 2. annotated tag; pushing it makes CI attach the APK to a GitHub Release
git tag -a v1.0.0 -m "Kumo v1.0.0"
git push origin main v1.0.0

# 3. make the repository public (irreversible exposure: forks and caches persist)
gh repo edit DivyankLosse/kumo --visibility public --accept-visibility-change-consequences
```

### 1.2 Credit commits to your GitHub profile (done before tagging)
```bash
git config user.email "<id>+DivyankLosse@users.noreply.github.com"   # from github.com/settings/emails
git rebase -r --root --exec 'git commit --amend --no-edit --reset-author'
git push --force-with-lease origin main
```
Do this **before** tagging and before going public.

---

## 2. Honest review (and the roast)

### What's genuinely strong
- **Offline-first and honest data.** Nutrition never invents numbers, blank stays blank, and the coach stays silent rather than faking a reply. Most habit apps fail at exactly this.
- **The sleep guardrail is enforced in the domain layer**, with tests, not just hinted at in the UI.
- **A real migration with a real migration test.** Most side projects find out their schema is wrong at version 3, in production.
- **Kumo is a genuine retention lever.** The focus walk → summit → park loop is the one part of the app with an emotional reward. Protect it.

### The roast
- **42 Room tables for an app with one user and zero days of usage data.** You built the database for a company. Half of those tables (outfits, products, haircuts, presentations) will have fewer than 5 rows each after a year.
- **The default schedule is a Gantt chart of burnout.** Weekdays are booked solid from 06:30 to 22:30. The rescheduler mathematically proves it: a missed 2-hour career block has *no free slot until the weekend*. The app is politely telling you your plan is impossible, and the plan treats that as a feature.
- **Ten modules, each about 60% deep.** Career, Fitness, Nutrition, Academics, Communication, Grooming, Posture, Social, Analytics, Companion. That makes it Notion with a bear. A user opens maybe three of these. You'll know which three after two weeks of real use, so build depth there, not breadth everywhere.
- **A cosmetic shop before retention exists.** Ten hats for a bear nobody has walked with for 30 days yet.
- **Manual-entry nutrition with no saved meals.** Typing "Dal, 18 g protein" twice a day is how food logging dies in week two.
- **Nine notification types.** Users mute the app after the third ping in one afternoon.
- **The year anchor expires.** "Days left in 2027" becomes a countdown to irrelevance on 1 January 2028.
- **Rive watermark plus a 55 MB debug APK** for a 28 KB animation. Fine for now, but not a launch look.

### High-ROI ("actually worth doing")
1. **Onboarding that builds the schedule from the user's answers** instead of your exact life. This is the single biggest unlock for anyone who isn't you.
2. **Slack-aware planning and an overload advisor.** Reserve buffer time; warn when a day is above about 85% booked or when back-to-back focus blocks run past 3 hours.
3. **One-tap logging from notifications and the widget**: "Done", "+250 ml water", "Snooze 15 min". Logging friction decides retention.
4. **Weekly review** (on-device facts first, AI narrative optional). It turns data into decisions, which is the reason anyone tracks anything.
5. **Saved meals and favourites** for food entry.
6. **Release hygiene**: signing, an Android App Bundle (AAB), R8 rules checked on a real device, and a focus timer that survives process death.
7. **Health Connect import** for sleep, weight and steps. It removes manual entry with real numbers.

### Low-ROI / traps (skip or defer)
- More shop items, more fur colours, 3D Kumo.
- A free-form AI chat tab. Structured AI tasks (§4) are worth more.
- Voice-recording analysis such as filler words or tone. It's privacy-heavy, hard to get right, and few people would use it.
- Outfit planner, product inventory, haircut log. Merge them into one "Grooming notes" list, or cut.
- Cloud sync or accounts. You'd need a backend, auth and conflict resolution; SAF backup to Google Drive covers 95% of the need.
- More CSV export types and more chart types.
- Additional widgets beyond the one Today widget.

---

## 3. Universal appeal and naming

### 3.1 From "my life" to "any life"
| Today (hardcoded) | Make it |
|---|---|
| `SeedData` = one student's week | **Templates**: Student, 9–5 worker, Shift worker, Freelancer, Exam prep. Onboarding asks wake/sleep, fixed blocks (college/work), commute, gym days and the main goal, then generates `task_templates`. |
| Sleep fixed at 22:30–06:30 | User-defined sleep window; the guardrail reads it from the profile, and `ScheduleRules` takes the window as parameters. |
| AI/ML roadmap only | **Roadmap library**: AI/ML, Web dev, Data analyst, UI/UX, Competitive exams, Custom. Same 4-phase shape, different topics. |
| Protein 100–130 g | Suggested range of 1.2–2.0 g/kg from body weight, shown as guidance with an "edit" link; diet type affects the meal suggestions only. |
| "Days left in 2027" | **Seasons**: a named goal period with start and end dates ("2027", "Semester 6", "90-day cut"). The countdown runs to the active season's end. |
| 24-hour clock, metric units, Monday week start | Settings plus locale defaults. |

### 3.2 Naming
You already picked **Kumo**. My recommendation is to keep it.
- It's short and warm, it's the mascot's name (one word to remember), and it isn't tied to any year.
- Japanese "kumo" means cloud (雲) or spider (蜘蛛). Harmless, but expect search collisions on the Play Store; pair it with a descriptor.
- **Store listing:** "Kumo: Focus & Life Planner".
- **Taglines:** "Build the person you want to become." (current) · "Small steps, steady climb." · "Walk the ridge, one day at a time."
- **The year becomes an edition, not the name:** "Kumo · Season 2027" inside the app.
- If Kumo hits a trademark problem, the backups from your shortlist were **Ridgeline** and **Dawnward**.

Before going public, search the Play Store, USPTO and EUIPO for "Kumo" in class 9 (software).

---

## 4. AI architecture for v1.0.1 (a production ML system, not a wrapper)

### 4.1 Where inference runs
| Job | Where | Why |
|---|---|---|
| Overload detection, sleep encroachment, streaks, completion trends, "missed block" patterns | **On-device, deterministic rules** (`InsightEngine`) | These are arithmetic, not ML. Rules are instant, offline, testable and explainable. |
| Short summaries and reflection rephrasing when no network | **On-device model, optional**: Gemini Nano via ML Kit GenAI / AICore where supported (recent Pixels and flagships), else none | Private and offline. Treat it as a bonus because device support is narrow. |
| Semantic note search ("what did I note about transformers?") | On-device text embeddings (MediaPipe Text Embedder, about 4 MB model) plus a small vector table in Room | Private, cheap, offline. |
| Weekly retrospective narrative, roadmap session planning, multi-signal coaching | **Backend proxy** (Antigravity-owned) calling a hosted LLM with structured JSON output | Needs stronger reasoning; keys stay server-side. |

### 4.2 Boundaries in the app
```
ui/        → ViewModels call use cases only
domain/    → InsightEngine (rules), FactSheetBuilder, CoachUseCases
             interfaces: CoachGateway, OnDeviceSummarizer
data/coach → ProxyCoachGateway (HTTP) | NoopCoachGateway (unconfigured)
             OnDeviceSummarizer impls: GeminiNano | Unavailable
```
- **FactSheet** (built on the device, the only thing the AI ever sees): a list of `{id, kind, value, unit, period}` facts, for example `{id:"f12", kind:"career_minutes", value:610, unit:"min", period:"2027-W10"}`. No raw notes, photos or recordings unless the user opts in per feature.
- **Grounding check:** the server must return `items[]` where every number in the text is tagged with `cites:[factId]`. The client re-checks each cited value against the FactSheet and drops any item with an uncited or mismatched number. That is how "never fabricate logs or nutrition numbers" is *enforced*, not just hoped for.
- **Versioning:** `promptId@version` and `schemaVersion` go in every request and are stored with each saved result (`weekly_reviews.promptVersion`), so you can re-run evals when a prompt changes.
- **Fallbacks:** unconfigured or offline → rules output plus template text ("You logged 10.2 h of career work, 73% of target; Tuesday and Thursday were missed."). The app never shows a spinner that turns into nothing.

### 4.3 Backend contract (for Antigravity)
```
POST /v1/coach/{task}        task ∈ weekly_review | overload_check | roadmap_plan | reflection_prompts
Headers: X-Install-Token (issued after a Play Integrity check), X-Schema-Version: 1
Request:  { promptVersion, locale, facts: [Fact], constraints: { sleepStart, sleepEnd, freeSlots: [...] } }
Response: { schemaVersion, model, promptVersion, items: [{ type, text, cites: [factId], action? }], latencyMs }
Errors:   non-2xx with { code, retryAfterSec? }   → the client shows "no reply", never invents one
```
- **Model routing:** a small or fast model for `reflection_prompts` and `overload_check` phrasing; a stronger model for `weekly_review` and `roadmap_plan`.
- **Structured output** is validated against a JSON schema on the server and re-validated on the client.
- **Rate limits:** a per-install token bucket (for example 30 requests per day), a 16 KB request cap, and a 45 s timeout.
- **Privacy:** no request bodies in logs (log only `task`, latency, token counts and model); zero retention; an opt-in toggle per task.
- **Evals:** about 50 golden FactSheets per task, scored for citation validity (target 100%), schema validity (100%) and a rubric score; run in CI on every prompt version bump.

### 4.4 The four AI features
| Feature | On-device part | AI part (optional) | Guardrail |
|---|---|---|---|
| **Schedule and overload advisor** | `OverloadAnalyzer`: booked % of the awake window, longest back-to-back focus run, buffer minutes, weekly career target vs feasible free slots | Phrases advice and proposes 1–2 swaps | Proposed moves go through `ScheduleRules.isWithinAwakeWindow` plus an overlap check before they are shown; anything inside the sleep window is discarded. |
| **Weekly retrospective** | Computes the week's facts across career, gym, nutrition, habits and energy check-ins; picks the top 3 deltas | Narrative with citations; "one thing to change" | Citation check (§4.2); offline template fallback. |
| **Roadmap assistant** | Splits the current phase's unfinished topics into 45–90 min sessions; maps them to free career slots | Suggests ordering, spots bottlenecks ("stuck on Backprop for 3 weeks") | Sessions are only ever placed into existing free slots; the user accepts each one. |
| **Reflection prompts** | Local bank of about 60 prompts, picked by context (missed day, streak, low energy) | Rephrases to match the week's facts | No mental-health claims; neutral wording; skippable. |

**Voice feedback:** defer. If you build it, keep it local: duration and speaking pace from on-device speech recognition only, with no audio upload.

### 4.5 External integrations
| Integration | Value | Auth / limits | Privacy and offline |
|---|---|---|---|
| **Health Connect** (sleep, weight, steps) | High: replaces manual sleep/weight entry with real data | Runtime permission per data type; no network | All on the device; read-only; the app keeps working without it |
| **SAF auto-backup to a chosen folder** (Drive, Files) | High: backup that actually happens | Persistable URI permission; no OAuth | User-chosen location; a weekly WorkManager job |
| **CalendarContract read** (import timetable and exams) | Medium | `READ_CALENDAR`, requested only on import | One-shot import; nothing is synced back |
| **Open Food Facts barcode lookup** | Medium (v1.1) | Free, no key; set a polite User-Agent with a generic app name | Values come from the database, not generated; the user confirms before saving; manual entry when offline |
| Weather / commute APIs | Low | n/a | Skip |
| LLM via the proxy | As described in §4 | Install token + rate limit | Opt-in; FactSheet only |

---

## 5. v1.0.1 specification

Database: **one migration, v2 → v3** (`MIGRATION_2_3`) with a `MigrationTest` for 2→3 and 1→3.

### P0-1 · Onboarding and schedule builder
- **Problem:** the app only fits one person. **Who benefits:** every new user, and you when your semester changes.
- **Schema:**
  - `profile` gains `weekStartsMonday INTEGER NOT NULL DEFAULT 1`, `use24h INTEGER NOT NULL DEFAULT 1`, `units TEXT NOT NULL DEFAULT 'metric'`, and `onboarded INTEGER NOT NULL DEFAULT 0`. Existing installs are set to 1.
  - New table `seasons(id PK, name, startDate, endDate, active)`.
- **UI:** a 5-step flow: wake/sleep → fixed blocks (college/work + days) → commute → training days and time → goal track + season. A preview of the generated week comes before saving.
- **Steps:**
  1. Add a `ScheduleTemplateGenerator` (pure Kotlin) that fills gaps with focus blocks and keeps ≥ 10% buffer.
  2. Replace `SeedData.templates()` with the generator.
  3. Make `ScheduleRules` take the user's sleep window.
- **Acceptance:**
  - A fresh install shows onboarding.
  - The generated templates never overlap and never enter the user's sleep window (property test over 200 random inputs).
  - An upgraded v1.0.0 install skips onboarding and keeps its data.

### P0-2 · Slack-aware planner and overload advisor
- **Problem:** fully booked days make every miss unrecoverable.
- **Schema:** none. Results are computed.
- **Domain:** `OverloadAnalyzer.analyze(day): OverloadReport(bookedPct, longestFocusRunMin, bufferMin, warnings[])`.
- **UI:**
  - A Planner banner ("Today is 97% booked: no room to recover a missed block") with a one-tap "Make room" that proposes demoting a low-priority block.
  - A small chip on the Home "Today" card.
- **Acceptance:**
  - A warning appears at ≥ 85% booked, for focus runs > 180 min, and when buffer < 30 min.
  - "Make room" never touches COLLEGE/COMMUTE blocks or the sleep window.
  - Unit tests cover each rule.

### P0-3 · One-tap logging
- **Problem:** every log costs 3–5 taps.
- **Schema:** none.
- **UI and system:**
  - Notification actions "Done" and "Snooze 15 min" on task reminders.
  - "+water" on the hydration reminder.
  - Glance widget buttons "+250 ml" and "Next block done" (`actionRunCallback`).
- **Acceptance:**
  - An action updates Room without opening the app; the widget refreshes within 2 s.
  - Instrumented tests drive each action through a `BroadcastReceiver`.

### P0-4 · Weekly review
- **Problem:** data without decisions.
- **Schema:**
  - `weekly_reviews(weekStart PK, wins TEXT, blockers TEXT, focusNext TEXT, generatedSummary TEXT NULL, promptVersion TEXT NULL, createdAt)`
  - `daily_checkins(date PK, energy INTEGER 1–5, mood INTEGER 1–5, note TEXT)`, since energy feeds the review.
- **UI:**
  - A Sunday 20:00 card leads to the review: auto facts (career hours vs target, gym sessions, protein days in range, sleep on target, habit rate), 3 prompts, and an optional "Summarize with AI".
  - A daily 2-tap energy/mood check-in on Home.
- **Acceptance:**
  - Facts match the Analytics numbers exactly (shared `FactSheetBuilder`).
  - With the AI on, every number shown passes the citation check.
  - Offline, the template summary appears.

### P0-5 · Release hygiene
- **Steps:**
  1. Signing config read from env/Gradle properties; CI uses repository secrets (`KUMO_KEYSTORE_B64`, `KUMO_KEY_PASSWORD`).
  2. Build an AAB for Play; split APKs per ABI for GitHub releases (~55 MB → ~15 MB).
  3. R8 keep rules for kotlinx-serialization (`BackupBundle`, coach DTOs) and Rive.
  4. A release smoke test on a device: launch, focus session, backup → restore.
  5. Persist a running focus session (start time, planned, category) in DataStore so it survives process death.
  6. Swap the Rive watermark only if you pay for a plan; otherwise keep it documented.
- **Acceptance:** the CI release job produces signed artifacts; the restore round trip passes on the release build; killing the app mid-session and reopening resumes the timer.

### P1 (after P0)
| # | Feature | Schema | Acceptance |
|---|---|---|---|
| 6 | Health Connect import (sleep, weight, steps) | `sleep_logs`, `body_weights` gain `source TEXT DEFAULT 'manual'` | Imported rows are labelled; manual rows are never overwritten; it works with the permission denied |
| 7 | Saved meals and favourites | `meal_templates(id, name, meal, calories?, proteinG?, carbsG?, fatG?)` | Logging a saved meal takes 2 taps; null values stay null |
| 8 | Coach proxy v1 (Antigravity) + `ProxyCoachGateway` | none client-side | Contract tests against a mock server; the citation check rejects a fabricated number |
| 9 | Roadmap library + assistant | `roadmap_tracks(id, name)`, `roadmap_phases.trackId` | Switching track keeps progress per track; sessions land only in free slots |
| 10 | Notification diet: 3 defaults on (morning, focus block, night), the rest opt-in | none | A fresh install posts at most 3 notifications a day by default |

### Defer
Shop expansion, voice analysis, a free-form chat tab, cloud accounts and sync, outfit and product inventory screens, more widgets.

### Execution order
- **Week 1:** P0-5 release hygiene (unblocks shipping) → P0-2 overload advisor → P0-3 one-tap logging.
- **Week 2:** P0-1 onboarding + v3 migration → P0-4 weekly review + check-ins.
- **Week 3+:** P1 in order 7 → 6 → 10 → 8 → 9.
- **Gate:** don't start P1 until you've used v1.0.1 daily for 14 days and know which three modules you actually open.

---

## Appendix: handoff prompt for Antigravity (coach backend)
> Build the Kumo coach proxy (FastAPI). Endpoints: `POST /v1/coach/{task}` for `weekly_review`, `overload_check`, `roadmap_plan` and `reflection_prompts`, plus `POST /v1/install-token`, which verifies a Play Integrity token and issues a per-install token.
>
> **Request:** `{promptVersion, locale, facts:[{id,kind,value,unit,period}], constraints:{sleepStart,sleepEnd,freeSlots}}`.
> **Response:** `{schemaVersion:1, model, promptVersion, items:[{type,text,cites:[factId],action?}], latencyMs}`.
>
> Requirements:
> - Validate the LLM output against the JSON schema.
> - Reject any item that contains a number not present in a cited fact.
> - Store prompts in a versioned registry (`promptId@version`).
> - Route `reflection_prompts` and `overload_check` to a small model, the others to a stronger one.
> - Per-install token bucket of 30 requests/day, 16 KB body cap, 45 s timeout.
> - Log only task, model, latency and token counts (no bodies); zero data retention.
> - Keep API keys in server env only.
>
> Tests: schema validation, citation rejection, rate limiting, oversized bodies, upstream failure → 502 with no fabricated text, and an eval runner over golden FactSheets that fails CI if citation validity drops below 100%.
