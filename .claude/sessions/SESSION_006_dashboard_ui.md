# Session 006: Dashboard UI Restructure

## Meta
- **Date:** 2026-02-11
- **Goal:** Add dashboard as main screen, move settings to separate screen with gear icon navigation
- **Status:** Complete

## Module Checklist
- [x] DashboardData in UserPreferences (data class + DataStore keys + flow + save)
- [x] Worker caches dashboard data after each fetch
- [x] DashboardScreen composable (score circle, info cards, refresh FAB, gear icon)
- [x] MainActivity refactored with Screen sealed class navigation + BackHandler

## Completed Modules
| Module | Validated | Date |
|--------|-----------|------|
| DashboardData in UserPreferences | Yes | 2026-02-11 |
| Worker cache | Yes | 2026-02-11 |
| DashboardScreen composable | Yes | 2026-02-11 |
| MainActivity navigation refactor | Yes | 2026-02-11 |

## Technical Decisions
- **Navigation:** State-based `sealed class Screen` (Dashboard / Settings) — no nav library needed for 2 screens
- **Dashboard data source:** Worker saves to DataStore → Dashboard reads via Flow (instant display from cache)
- **Refresh button:** Triggers `AuroraUpdateWorker.schedule()` (immediate OneTimeWorker + periodic reschedule)
- **Score display:** Color-coded circle matching widget color scheme (gray/green/yellow/orange/red)
- **Nullable visibility:** If weather unavailable, falls back to showing raw aurora probability

## Files Modified
- `data/preferences/UserPreferences.kt` -- Added DashboardData class, DataStore keys, dashboardFlow, saveDashboardData()
- `worker/AuroraUpdateWorker.kt` -- Caches DashboardData after score computation
- `ui/DashboardScreen.kt` -- **New** — Main dashboard screen
- `MainActivity.kt` -- Screen sealed class, when() routing, BackHandler, TopAppBar with back arrow on ConfigScreen

## Future Notes
- **Phase 5 will add:** Multi-day Kp forecast (NOAA 3-day), multi-day cloud forecast (Open-Meteo forecast_days=3), combined daily visibility predictions, 4x2 widget with Kp graph

## Handoff Notes
- App now opens on Dashboard (score + aurora/kp/clouds/sun cards)
- Settings accessible via gear icon (top right)
- Back navigation works (arrow + system back button)
- Dashboard reads cached data from DataStore for instant display
