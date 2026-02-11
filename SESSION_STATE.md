# Aurora Widget - Session State

> **Claude : Appliquer le protocole de session (CLAUDE.md)**
> - Creer/mettre a jour la session en temps reel
> - Valider apres chaque module avec : [Module] complete. **Test it:** [...] Waiting for validation.
> - Ne pas continuer sans validation utilisateur

## Current Session

| # | Name | Status | Branch |
|---|------|--------|--------|
| 001 | Project Setup | Complete | main |
| 002 | Phase 1 - Widget End-to-End | Complete | main |
| 003 | Phase 2 - Weather & Combined Score | Complete | main |
| 004 | Phase 3 - Configuration & Location | In Progress (paused) | main |

## Build Order Progress

### Phase 1: Minimal Functional Widget
- [x] Project structure + Gradle config (validated 2026-02-11)
- [x] Timber logging setup (included in project setup)
- [x] OVATION API call (NoaaRepository) (validated 2026-02-11)
- [x] AuroraCalculator (probability extraction) (validated 2026-02-11)
- [x] Widget layout 2x1 (RemoteViews XML) (validated 2026-02-11)
- [x] AppWidgetProvider (validated 2026-02-11)
- [x] WorkManager periodic refresh (validated 2026-02-11)
- [x] REVIEW: end-to-end widget (validated 2026-02-11)

### Phase 2: Weather & Combined Score
- [x] Open-Meteo API (WeatherRepository) (validated 2026-02-11)
- [x] AuroraCalculator updated (combined score) (validated 2026-02-11)
- [x] Widget layout 3x2 + Kp + immediate fetch (validated 2026-02-11)
- [x] REVIEW: combined score (validated 2026-02-11)

### Phase 3: Configuration & Location
- [x] DataStore preferences (validated 2026-02-11)
- [x] MainActivity Compose config screen (validated 2026-02-11)
- [x] GPS + permissions (validated 2026-02-11)
- [x] Manual location input (validated 2026-02-11)
- [x] Refresh frequency setting (validated 2026-02-11)
- [x] Widget tap → config (validated 2026-02-11)
- [ ] REVIEW: location & settings

### Phase 4: Notifications
- [ ] Notification channel
- [ ] AuroraNotifier
- [ ] Configurable threshold
- [ ] Cooldown anti-spam
- [ ] REVIEW: notifications

### Phase 5: Polish
- [ ] Widget 4x2 + Kp graph
- [ ] Offline mode
- [ ] Dark/light theme
- [ ] Error handling
- [ ] Security audit
- [ ] Dependency audit
- [ ] FINAL REVIEW

## Known Issues
- ~~**Launcher icon:** Fixed — adaptive icon XML pointe maintenant vers maurora.png foreground~~
- ~~**Position hardcodee:** Fixed — position lue depuis DataStore, configurable via GPS ou saisie manuelle~~
