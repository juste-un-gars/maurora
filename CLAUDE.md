# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## QUICK REFERENCE - READ FIRST

**Critical rules that apply to EVERY session:**

1. **Incremental only** — Max 150 lines per iteration, STOP for validation
2. **No hardcoding** — No secrets, paths, credentials in code (use `local.properties` or `BuildConfig`)
3. **Logs from day 1** — Use `android.util.Log` or Timber with configurable log levels
4. **Security audit** — MANDATORY before release build
5. **Stop points** — Wait for "OK"/"validated" after each module

**If unsure, read the relevant section below.**

---

## Project Context

**Project Name:** Aurora Widget (maurora)
**Tech Stack:** Android / Kotlin / Gradle (Kotlin DSL)
**Primary Language:** Kotlin
**Package:** `com.franck.aurorawidget`
**Min SDK:** API 26 (Android 8.0)
**Key Dependencies:** WorkManager, Retrofit/ktor-client, kotlinx.serialization, DataStore, Jetpack Compose (activity only)
**Architecture Pattern:** Widget-centric with `AppWidgetProvider` + `WorkManager` + Repository pattern

### What this app does

Home screen widget displaying aurora borealis observation probability from the user's location. Combines NOAA aurora data with local weather to compute a real visibility score.

### Core data flow

```
WorkManager (periodic) → Repositories (NOAA + Open-Meteo APIs)
    → AuroraCalculator (visibility score) → Widget update via RemoteViews
```

### Visibility Score Formula

```
score = aurora_probability * (1 - cloud_cover/100) * night_factor
```
- `aurora_probability`: OVATION grid value (0-100) for nearest lat/lon point (bilinear interpolation)
- `cloud_cover`: Open-Meteo value (0-100)
- `night_factor`: 1.0 between sunset/sunrise, 0.0 otherwise (progressive transition during golden hour)

### Color Coding

| Range | Color | Meaning |
|-------|-------|---------|
| 0-5% | Gray | Nothing to see |
| 5-20% | Green | Unlikely |
| 20-50% | Yellow | Possible |
| 50-80% | Orange | Likely |
| 80-100% | Red | Go outside |

### Data Sources (free, no API key)

**NOAA SWPC:**
- `services.swpc.noaa.gov/json/ovation_aurora_latest.json` — Aurora probability grid (~30 min updates, ~1-2 MB)
- `services.swpc.noaa.gov/products/noaa-planetary-k-index.json` — Current Kp (3h updates)
- `services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json` — 3-day Kp forecast

**Open-Meteo (preferred for weather):**
- `api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=cloud_cover,visibility&daily=sunrise,sunset&timezone=auto`

---

## Development Philosophy

### Golden Rule: Incremental Development

**NEVER write large amounts of code without validation.**

```
One module → Test → User validates → Next module
```

**Per iteration limits:**
- 1-3 related files maximum
- ~50-150 lines of new code
- Must be independently testable

### Mandatory Stop Points

Claude MUST stop and wait for user validation after:
- Widget provider or layout changes
- API integration (NOAA, Open-Meteo)
- WorkManager scheduling setup
- Location/permission handling
- Notification system
- Any data calculation logic (AuroraCalculator)

**Stop format:**
```
[Module] complete.

**Test it:**
1. [Step 1]
2. [Step 2]
Expected: [Result]

Waiting for your validation before continuing.
```

### Code Hygiene Rules (MANDATORY)

**Goal: Application must be portable and buildable without code changes.**

**NEVER hardcode in source files:**
- Passwords, API keys, tokens, secrets
- Absolute paths (`C:\Users\...`, `/home/user/...`)
- Environment-specific URLs

**ALWAYS use instead:**
- `local.properties` for local secrets (never committed)
- `BuildConfig` fields for build-time configuration
- `gradle.properties` for shared non-secret config
- `DataStore` for user preferences at runtime
- Relative paths or Android system paths (`context.filesDir`, `context.cacheDir`)

### Logging Standards

**Use Android logging from day 1.** Prefer Timber for tag-free logging.

```kotlin
// Setup in Application class
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}

// Usage
Timber.d("OVATION data fetched: %d points", points.size)
Timber.w("API call failed, using cached data")
Timber.e(exception, "WorkManager update failed")
```

**What to log:**
- API requests/responses (status, duration, data size)
- Widget update events
- WorkManager task execution (start, success, failure)
- Location changes
- Score calculations (inputs and result)
- Errors with stack traces

**NEVER log:**
- Full API response bodies in release builds
- User location coordinates in release builds (privacy)

### Development Order (Enforce)

1. **Foundation first** — Project structure, Gradle config, basic widget
2. **Test foundation** — Don't continue if broken
3. **Core features** — One by one, tested
4. **Advanced features** — Only after core works

### File Size Guidelines

**Target sizes (lines of code):**
- **< 300** : ideal
- **300-500** : acceptable
- **500-800** : consider splitting
- **> 800** : must split

---

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew test --tests "*.AuroraCalculatorTest"  # Run a single test class
./gradlew connectedAndroidTest   # Run instrumented tests (device/emulator)
./gradlew lint                   # Run Android lint
./gradlew clean                  # Clean build artifacts
./gradlew dependencies           # Show dependency tree
```

---

## Key Constraints

- **RemoteViews limitation:** Widget layouts can only use `TextView`, `ImageView`, `LinearLayout`, `RelativeLayout`, `FrameLayout` — no custom views, no Compose
- **WorkManager minimum:** 15-minute periodic interval (Android system constraint)
- **OVATION JSON:** Large file (~1-2 MB). Parse once, extract only points near user position. Cache last response for offline display
- **NOAA rate limiting:** One call per 15-30 min per user is reasonable
- **Widget sizes:** Small (2x1), Medium (3x2), Large (4x2)

---

## Session Management

### Quick Start

**Continue work:** `"continue"` or `"let's continue"`
**New session:** `"new session: Feature Name"`

### File Structure

- **SESSION_STATE.md** (root) — Overview and session index
- **.claude/sessions/SESSION_XXX_[name].md** — Detailed session logs

**Naming:** `SESSION_001_project_setup.md`

### SESSION_STATE.md Header (Required)

SESSION_STATE.md **must** start with this reminder block:

```markdown
# Aurora Widget - Session State

> **Claude : Appliquer le protocole de session (CLAUDE.md)**
> - Creer/mettre a jour la session en temps reel
> - Valider apres chaque module avec : [Module] complete. **Test it:** [...] Waiting for validation.
> - Ne pas continuer sans validation utilisateur
```

### Session Template

```markdown
# Session XXX: [Feature Name]

## Meta
- **Date:** YYYY-MM-DD
- **Goal:** [Brief description]
- **Status:** In Progress / Blocked / Complete

## Current Module
**Working on:** [Module name]
**Progress:** [Status]

## Module Checklist
- [ ] Module planned (files, dependencies, test procedure)
- [ ] Code written
- [ ] Self-tested by Claude
- [ ] User validated <-- REQUIRED before next module

## Completed Modules
| Module | Validated | Date |
|--------|-----------|------|
| ... | ... | ... |

## Next Modules (Prioritized)
1. [ ] [Next module]
2. [ ] [Following module]

## Technical Decisions
- **[Decision]:** [Reason]

## Issues & Solutions
- **[Issue]:** [Solution]

## Files Modified
- `path/file.ext` -- [What/Why]

## Handoff Notes
[Critical context for next session]
```

### Session Rules

**MUST DO:**
1. Read CLAUDE.md and current session first
2. Update session file in real-time
3. Wait for validation after each module
4. Fix bugs before new features

**NEW SESSION when:**
- New major feature/module
- Current session goal complete
- Different project area

---

## Module Workflow

### 1. Plan (Before Coding)

```
Module: [Name]
Purpose: [One sentence]
Files: [List]
Depends on: [Previous modules]
Test procedure: [How to verify]
Security concerns: [If any]
```

### 2. Implement

- Write minimal working code
- Include error handling
- Add KDoc for public APIs

### 3. Validate

**Functional:**
- [ ] Builds without errors (`./gradlew assembleDebug`)
- [ ] Lint passes (`./gradlew lint`)
- [ ] Unit tests pass (`./gradlew test`)
- [ ] Expected behavior verified on device/emulator

**Security (if applicable):**
- [ ] No hardcoded secrets or credentials
- [ ] User location data handled with privacy in mind
- [ ] Network calls use HTTPS only

### 4. User Confirmation

**DO NOT proceed until user says "OK", "validated", or "continue"**

---

## Build Order — Aurora Widget

```
Phase 1: Minimal Functional Widget (validate before Phase 2)
├── [ ] Project structure + Gradle config → builds without error
├── [ ] Timber logging setup
├── [ ] OVATION API call (NoaaRepository) → data fetched
├── [ ] AuroraCalculator (probability extraction) → correct value for position
├── [ ] Widget layout 2x1 (RemoteViews XML) → displays on home screen
├── [ ] AppWidgetProvider → shows aurora % with color
├── [ ] WorkManager periodic refresh (30 min) → auto-updates
└── [ ] REVIEW: widget works end-to-end with fixed position

Phase 2: Weather & Combined Score (validate before Phase 3)
├── [ ] Open-Meteo API call (WeatherRepository) → cloud cover + sunrise/sunset
├── [ ] AuroraCalculator updated → combined visibility score
├── [ ] Widget layout 3x2 → visibility + Kp + cloud icon + last update
└── [ ] REVIEW: combined score displays correctly

Phase 3: Configuration & Location (validate before Phase 4)
├── [ ] DataStore preferences (UserPreferences)
├── [ ] MainActivity with Jetpack Compose → config screen
├── [ ] GPS location with permission handling
├── [ ] Manual location input (lat/lon or city search)
├── [ ] Refresh frequency setting (15/30/60 min)
├── [ ] Widget tap → opens config activity
└── [ ] REVIEW: location and settings work correctly

Phase 4: Notifications (validate before Phase 5)
├── [ ] Notification channel "Alertes Aurora"
├── [ ] AuroraNotifier → alert when score > threshold
├── [ ] Configurable threshold in preferences
├── [ ] Cooldown anti-spam (e.g., max 1 notification per 3h)
└── [ ] REVIEW: notifications trigger correctly

Phase 5: Polish (FINAL)
├── [ ] Widget layout 4x2 with mini Kp 3-day graph
├── [ ] Offline mode (graceful "cached data" display)
├── [ ] Dark/light theme adaptive
├── [ ] Error handling for network failures
├── [ ] Full security audit
├── [ ] Dependency audit (./gradlew dependencies)
└── [ ] FINAL REVIEW
```

---

## Documentation Standards

### File Header (Kotlin)

```kotlin
/**
 * @file FileName.kt
 * @description Brief purpose
 */
```

### Function Documentation (KDoc)

```kotlin
/**
 * Brief description.
 * @param name Description
 * @return Description
 */
```

---

## Pre-Launch Security Audit

### When to Run

**MANDATORY before release build.**

### Checklist

#### 1. Code Review
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] No hardcoded paths
- [ ] No sensitive data in logs (release build)
- [ ] User location handled with privacy (not logged, not sent to unauthorized services)
- [ ] No debug code left in release

#### 2. Android-Specific
- [ ] `android:debuggable="false"` in release manifest
- [ ] ProGuard/R8 enabled for release
- [ ] Network security config enforces HTTPS
- [ ] Permissions are minimal (only what's needed)
- [ ] `exported` attributes set correctly on components

#### 3. Dependency Audit
```bash
./gradlew dependencies
# Check for known vulnerabilities in dependencies
```

#### 4. Network Security
- [ ] All API calls over HTTPS
- [ ] Certificate pinning considered for NOAA endpoints
- [ ] No sensitive data in URL parameters

---

## Git Integration

### Branch Naming
`feature/session-XXX-brief-name`

### Commit Message
```
Session XXX: [Summary]

- Change 1
- Change 2
```

### .gitignore (Android)

The project `.gitignore` must include:
```gitignore
# Android/Gradle
.gradle/
build/
local.properties
*.apk
*.aab
*.ap_

# IDE
.idea/
*.iml

# OS
.DS_Store
Thumbs.db

# Secrets
*.key
*.pem
*.jks
*.keystore

# Logs
logs/
*.log

# Cache
.cache/
```

---

## Android Permissions

- `INTERNET` — API calls
- `ACCESS_COARSE_LOCATION` — Approximate position (sufficient)
- `ACCESS_FINE_LOCATION` — Optional precision
- `RECEIVE_BOOT_COMPLETED` — Restart worker on boot
- `POST_NOTIFICATIONS` — Android 13+

---

## Quick Commands

| Command | Action |
|---------|--------|
| `continue` | Resume current session |
| `new session: [name]` | Start new session |
| `save progress` | Update session file |
| `validate` | Mark current module as validated |
| `show plan` | Display remaining modules |
| `security audit` | Run full pre-launch security checklist |

---

## File Standards

- **Encoding:** UTF-8 with LF line endings
- **Timestamps:** ISO 8601 (YYYY-MM-DD HH:mm)
- **Language:** Code, comments, and commits in English. Documentation may be in French.
