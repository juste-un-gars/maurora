# Session 001: Project Setup

## Meta
- **Date:** 2026-02-11
- **Goal:** Create Android project structure, Gradle config, and ensure it builds
- **Status:** Complete

## Current Module
**Working on:** —
**Progress:** All done

## Module Checklist
- [x] Module planned (files, dependencies, test procedure)
- [x] Code written
- [x] Self-tested by Claude (BUILD SUCCESSFUL)
- [x] User validated (compiles and launches)

## Completed Modules
| Module | Validated | Date |
|--------|-----------|------|
| Project structure + Gradle config | Yes | 2026-02-11 |
| Timber logging setup | Yes (included above) | 2026-02-11 |

## Technical Decisions
- **HTTP client:** ktor-client 3.0.3 (Kotlin-native, coroutine-friendly)
- **JSON:** kotlinx.serialization 1.7.3
- **Weather API:** Open-Meteo (no API key needed)
- **Gradle:** 8.11.1, AGP 8.7.3, Kotlin 2.1.0
- **Compile SDK:** 35, Min SDK: 26, Target SDK: 35
- **Version catalog:** gradle/libs.versions.toml for centralized dependency management
- **App name:** Maurora (package: com.franck.aurorawidget)

## Issues & Solutions
- **README.md encoding:** Was UTF-16 → re-encoded to UTF-8
- **Launcher icon (OPEN):** Les PNG mipmap (maurora.png resized) sont ignorees sur API 26+ car `mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon avec vector placeholder) a priorite. Fix pour prochaine session: supprimer les XML adaptive icon ou convertir maurora.png en foreground layer.

## Files Created
- `settings.gradle.kts` — Project settings (rootProject = "Maurora")
- `build.gradle.kts` — Root build file (plugin declarations)
- `gradle.properties` — JVM args, AndroidX config
- `gradle/libs.versions.toml` — Version catalog (all dependencies)
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.11.1
- `gradle/wrapper/gradle-wrapper.jar` — Wrapper bootstrap
- `gradlew` / `gradlew.bat` — Wrapper scripts
- `local.properties` — SDK path (not committed)
- `.gitignore` — Android/Gradle/IDE ignores
- `app/build.gradle.kts` — App module config with all dependencies
- `app/proguard-rules.pro` — R8 rules for kotlinx.serialization
- `app/src/main/AndroidManifest.xml` — Manifest with MainActivity
- `app/src/main/java/com/franck/aurorawidget/AuroraWidgetApp.kt` — Application class + Timber init
- `app/src/main/java/com/franck/aurorawidget/MainActivity.kt` — Placeholder Compose activity
- `app/src/main/res/values/strings.xml` — App name = "Maurora"
- `app/src/main/res/values/themes.xml` — Material theme
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — Adaptive icon foreground (placeholder)
- `app/src/main/res/drawable/ic_launcher_background.xml` — Adaptive icon background
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — Adaptive icon definition
- `app/src/main/res/mipmap-*/ic_launcher.png` — Resized maurora.png (48-192px)
- `app/src/main/res/mipmap-*/ic_launcher_round.png` — Round variant

## Handoff Notes
- Projet compile et se lance. Nom = Maurora.
- **Bug icon a fixer en debut de prochaine session:** Les fichiers adaptive icon XML (`mipmap-anydpi-v26/`) prennent le dessus sur les PNG. Soit les supprimer, soit faire pointer le foreground vers maurora.png.
- Timber est deja initialise dans `AuroraWidgetApp.kt`.
- Prochaine etape: OVATION API call (NoaaRepository) — debut de Phase 1 module 3.
