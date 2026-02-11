# Session 003: Phase 2 - Weather & Combined Score

## Meta
- **Date:** 2026-02-11
- **Goal:** Complete Phase 2 — WeatherRepository, combined visibility score, widget 3x2
- **Status:** Complete

## Current Module
**Working on:** —
**Progress:** Phase 2 complete

## Completed Modules
| Module | Validated | Date |
|--------|-----------|------|
| WeatherRepository (Open-Meteo API) | Yes | 2026-02-11 |
| AuroraCalculator (combined visibility score) | Yes | 2026-02-11 |
| Widget 3x2 + Kp + integration | Yes | 2026-02-11 |
| Immediate fetch fix (OneTimeWorkRequest) | Yes | 2026-02-11 |
| REVIEW: combined score end-to-end | Yes | 2026-02-11 |

## Technical Decisions
- **Open-Meteo API:** Free, no key. Returns cloud_cover, visibility, sunrise/sunset
- **URL format:** `api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=cloud_cover,visibility&daily=sunrise,sunset&timezone=auto`

## Files Created/Modified
- `app/src/main/java/.../data/model/WeatherData.kt` — Weather API models + conversion
- `app/src/main/java/.../data/remote/WeatherRepository.kt` — Open-Meteo API fetch
- `app/src/test/.../data/model/WeatherDataTest.kt` — 2 unit tests
- `app/src/main/java/.../data/calculator/AuroraCalculator.kt` — Added computeVisibilityScore + computeNightFactor
- `app/src/test/.../data/calculator/AuroraCalculatorTest.kt` — 10 new tests (night factor, combined score)
- `app/src/main/java/.../data/model/KpData.kt` — Kp index model
- `app/src/main/java/.../data/remote/NoaaRepository.kt` — Added fetchKpIndex()
- `app/src/main/res/layout/widget_aurora_medium.xml` — 3x2 widget layout
- `app/src/main/res/xml/aurora_widget_medium_info.xml` — Medium widget metadata
- `app/src/main/res/values/strings.xml` — Added medium widget strings
- `app/src/main/java/.../widget/AuroraWidgetProvider.kt` — Refactored: WidgetDisplayData, updateSmallWidget, updateMediumWidget, AuroraMediumWidgetProvider
- `app/src/main/java/.../worker/AuroraUpdateWorker.kt` — Fetches weather + Kp, computes combined score
- `app/src/main/AndroidManifest.xml` — Registered AuroraMediumWidgetProvider

## Technical Decisions
- **Open-Meteo API:** Free, no key. cloud_cover + sunrise/sunset with `timezone=auto`
- **Visibility formula:** `score = aurora_prob * (1 - cloud/100) * night_factor`
- **Night factor:** 30-min twilight transition after sunset / before sunrise
- **Kp endpoint:** NOAA `noaa-planetary-k-index.json` — parsed as `List<List<String>>`, last entry = latest
- **Immediate fetch:** OneTimeWorkRequest at app startup to avoid 30-min delay
- **Two widget types:** Small (2x1, score only) + Medium (3x2, score + cloud + Kp + time)
- **WidgetDisplayData:** Shared data class for both widget types

## Handoff Notes
- **Phase 2 complete.** Combined visibility score works end-to-end.
- **Position still hardcoded** Paris (48.86, 2.35) → Phase 3 GPS.
- **Next:** Phase 3 — DataStore, Compose config activity, GPS location, settings.
