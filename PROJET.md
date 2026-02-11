# Aurora Widget — Widget Android de prévision d'aurores boréales

## Objectif

Application Android minimaliste dont le seul but est d'afficher un **widget sur l'écran d'accueil** montrant la probabilité de voir une aurore boréale depuis la position de l'utilisateur. Pas d'activité principale complexe — juste le widget et un écran de configuration minimal.

## Fonctionnalités

### Widget (écran d'accueil)

- Affiche la **probabilité d'observation** (%) pour la position de l'utilisateur
- Code couleur visuel :
  - Gris : 0-5% (rien à voir)
  - Vert : 5-20% (peu probable)
  - Jaune : 20-50% (possible)
  - Orange : 50-80% (probable)
  - Rouge : 80-100% (fonce dehors)
- Affiche l'**indice Kp** actuel (0-9)
- Affiche la **couverture nuageuse** locale (pour savoir si on pourrait réellement voir quelque chose)
- Indicateur combiné : probabilité aurore × ciel dégagé = **score de visibilité réel**
- Horodatage de la dernière mise à jour
- Tap sur le widget → ouvre l'écran de détails/configuration

### Écran principal (activité)

- Configuration de la **localisation** : GPS automatique ou position fixée manuellement (lat/lon ou recherche ville)
- Réglage de la **fréquence de rafraîchissement** (15 min, 30 min, 1h)
- Option de **notification** quand la probabilité dépasse un seuil configurable (ex: > 30%)
- Vue détaillée avec :
  - Prévision Kp sur 3 jours (graphique simple)
  - Probabilité aurore actuelle
  - Météo locale (nuages, visibilité)
  - Heure du coucher/lever du soleil (les aurores ne sont visibles que la nuit)

## Sources de données (APIs gratuites, sans clé)

### NOAA SWPC — Données aurorales

Toutes les données sont en accès libre, sans authentification.

| Donnée | Endpoint | Format | Fréquence MAJ |
|--------|----------|--------|----------------|
| Probabilité aurore (grille lat/lon) | `https://services.swpc.noaa.gov/json/ovation_aurora_latest.json` | JSON | ~30 min |
| Indice Kp actuel | `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json` | JSON | 3h |
| Prévision Kp 3 jours | `https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json` | JSON | Variable |
| Vent solaire (Bz, vitesse) | `https://services.swpc.noaa.gov/products/summary/solar-wind-mag-field.json` | JSON | Temps réel |

**Endpoint principal — OVATION Aurora :**

Le fichier `ovation_aurora_latest.json` contient une grille de coordonnées avec la probabilité d'aurore pour chaque point. Structure attendue :
```json
[
  {"Longitude": 0, "Latitude": 40, "Aurora": 3},
  {"Longitude": 1, "Latitude": 40, "Aurora": 5},
  ...
]
```

La valeur `Aurora` est une intensité (0-100). Pour obtenir la probabilité à un point donné :
1. Récupérer la position GPS de l'utilisateur
2. Trouver le point de grille le plus proche (interpolation bilinéaire pour plus de précision)
3. La valeur correspond à la probabilité d'observation en %

### OpenWeatherMap — Météo locale (clé API gratuite)

- **Plan gratuit** : 1000 appels/jour, largement suffisant pour un widget
- Inscription requise sur https://openweathermap.org/api
- Endpoint : `https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={API_KEY}`
- Données utiles : `clouds.all` (couverture nuageuse en %), `visibility`, `sys.sunset`, `sys.sunrise`

### Alternative météo sans clé : Open-Meteo

- **Aucune clé API requise**, totalement gratuit
- Endpoint : `https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=cloud_cover,visibility&daily=sunrise,sunset&timezone=auto`
- Préférer cette option pour la simplicité (pas de gestion de clé API)

## Stack technique

- **Langage** : Kotlin
- **SDK minimum** : API 26 (Android 8.0)
- **Build** : Gradle avec Kotlin DSL
- **Architecture** : Simple, pas d'over-engineering
  - `AppWidgetProvider` pour le widget
  - `WorkManager` pour les mises à jour périodiques en arrière-plan
  - `Retrofit` ou `ktor-client` pour les appels HTTP
  - `kotlinx.serialization` pour le parsing JSON
  - `DataStore` (Preferences) pour la configuration utilisateur
- **Pas de dépendance lourde** : pas de Room (pas de BDD nécessaire), pas de Jetpack Compose pour le widget (RemoteViews obligatoire)
- L'écran de configuration peut utiliser **Jetpack Compose** pour l'UI de l'activité

## Structure du projet

```
app/
├── src/main/
│   ├── java/com/franck/aurorawidget/
│   │   ├── AuroraWidget.kt              # AppWidgetProvider principal
│   │   ├── AuroraWidgetReceiver.kt       # BroadcastReceiver pour les updates
│   │   ├── MainActivity.kt              # Écran configuration + détails
│   │   ├── data/
│   │   │   ├── NoaaRepository.kt         # Appels API NOAA
│   │   │   ├── WeatherRepository.kt      # Appels API Open-Meteo
│   │   │   ├── models/                   # Data classes pour les réponses JSON
│   │   │   │   ├── OvationData.kt
│   │   │   │   ├── KpIndex.kt
│   │   │   │   └── WeatherData.kt
│   │   │   └── AuroraCalculator.kt       # Logique de calcul du score combiné
│   │   ├── worker/
│   │   │   └── AuroraUpdateWorker.kt     # WorkManager pour refresh périodique
│   │   ├── notification/
│   │   │   └── AuroraNotifier.kt         # Gestion des notifications d'alerte
│   │   └── preferences/
│   │       └── UserPreferences.kt        # DataStore preferences
│   ├── res/
│   │   ├── layout/
│   │   │   └── widget_aurora.xml         # Layout du widget (RemoteViews)
│   │   ├── xml/
│   │   │   └── aurora_widget_info.xml    # Métadonnées du widget
│   │   └── drawable/                     # Icônes, backgrounds par niveau
│   └── AndroidManifest.xml
```

## Logique de calcul du score de visibilité

```
score_visibilite = probabilite_aurore × (1 - couverture_nuageuse/100) × facteur_nuit

où :
  - probabilite_aurore : valeur OVATION (0-100)
  - couverture_nuageuse : cloud_cover de Open-Meteo (0-100)
  - facteur_nuit : 1.0 si entre coucher et lever du soleil, 0.0 sinon
    (avec transition progressive pendant l'heure dorée)
```

## Tailles de widget

Supporter plusieurs tailles :
- **Petite (2×1)** : juste le % de visibilité avec couleur de fond
- **Moyenne (3×2)** : % visibilité + Kp + icône nuages + dernière MAJ
- **Grande (4×2)** : tout + mini-graphe Kp 3 jours

## Notifications

- Canal de notification dédié "Alertes Aurora"
- Se déclenche quand `score_visibilite` dépasse le seuil configuré par l'utilisateur
- Cooldown configurable pour ne pas spammer (ex: 1 notification max toutes les 3h)
- La notification inclut : score, Kp actuel, couverture nuageuse

## Permissions Android requises

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />  <!-- Position approximative suffisante -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />    <!-- Optionnel, pour plus de précision -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />  <!-- Relancer le worker au boot -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />      <!-- Android 13+ -->
```

## Phases de développement

### Phase 1 — Widget fonctionnel minimal
- Appel OVATION JSON + extraction probabilité pour une position fixe
- Widget 2×1 affichant le % avec code couleur
- Rafraîchissement toutes les 30 min via WorkManager

### Phase 2 — Météo et score combiné
- Intégration Open-Meteo (couverture nuageuse, sunrise/sunset)
- Calcul du score de visibilité réel
- Widget moyen (3×2) avec toutes les infos

### Phase 3 — Configuration et localisation
- Écran de configuration (Compose)
- GPS automatique ou position manuelle
- Réglages de fréquence de rafraîchissement

### Phase 4 — Notifications
- Alertes quand le score dépasse le seuil
- Canal de notification dédié
- Cooldown anti-spam

### Phase 5 — Polish
- Widget grande taille avec mini-graphe Kp
- Animations/transitions douces sur le widget
- Gestion des erreurs réseau (affichage "hors ligne" gracieux)
- Thème clair/sombre adaptatif

## Notes techniques

- Les widgets Android utilisent `RemoteViews` — pas de Compose, pas de vues custom complexes. Se limiter aux vues supportées : `TextView`, `ImageView`, `LinearLayout`, `RelativeLayout`, `FrameLayout`.
- `WorkManager` avec `PeriodicWorkRequest` pour les mises à jour. Minimum 15 minutes d'intervalle (contrainte Android).
- Attention au rate limiting sur les APIs NOAA : elles sont gratuites mais pas prévues pour des millions de requêtes. Un appel toutes les 15-30 min par utilisateur est largement raisonnable.
- Le fichier OVATION JSON peut faire ~1-2 Mo. Le parser une fois et extraire uniquement les points proches de la position utilisateur.
- Stocker en cache la dernière réponse NOAA pour affichage offline.
