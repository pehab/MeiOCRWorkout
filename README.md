# MeiOCRWorkout – Alpha MVP 0.7

Native Android app for configurable OCR workout simulation.

- App: **MeiOCRWorkout**
- Package / namespace / applicationId: **de.haberland.meiocrworkout**
- Kotlin + Jetpack Compose
- Room/SQLite local persistence
- fully offline; no account or backend


## Alpha polish in 0.7

### Launcher icon

MeiOCRWorkout now ships with its own adaptive launcher icon: a dark OCR-rig frame with the app's lime accent and a compact white `M`/obstacle mark. Android 13+ also gets a monochrome themed-icon resource.

### Version display

The Profile screen shows the current `versionName` and `versionCode` directly from `BuildConfig`. Current alpha: **0.7.0 (7)**.

### Google Play in-app update foundation

The app now includes Google's Play In-App Update library and performs a silent availability check on app startup.

- available updates use the **flexible** Play update flow
- download can continue while the app is used
- if the download finishes during a workout, MeiOCRWorkout does **not** interrupt the workout
- once outside the workout screen, the app offers `INSTALLIEREN` / `SPÄTER`
- if an already downloaded update is detected after returning to the app, the install prompt is restored
- locally sideloaded alpha APKs are not owned by Google Play, so the Play update check simply has nothing to do; the same code becomes active once builds are installed through a Play testing/production track

Dependencies: `com.google.android.play:app-update:2.1.0` and `app-update-ktx:2.1.0`.

## App structure

The normal app has three areas:

1. **Trainieren** – choose profile + workout mode + target, then START.
2. **Profile** – configure what is available at a location/setup.
3. **Historie** – locally saved completed workouts and round details.

Once START is pressed, the UI switches to an immersive workout screen. There is no settings/back navigation there:

- tap anywhere = next round
- compact `TRAINING BEENDEN` button = manual abort
- system bars hidden
- screen kept awake

## Profiles

Workout mode and profile are deliberately independent.

Examples:

- `Daheim` + 45-minute AMRAP
- `Garten` + 10 km
- `Garten` + 8 rounds
- `Daheim` + open AMRAP

Each profile owns three weighted pools:

- **Distanzen** – one-way distance. `0 m` is supported and means direct obstacle-to-obstacle transition.
- **Streckenbelastungen** – bodyweight/course-load simulation such as Burpees, Bear Crawl, Lunges. An optional `Keine` entry represents a free turnaround.
- **Hindernisse** – rig, carry, rope, walls, etc.

Every item can be enabled/disabled and weighted 1–20. Names and optional display details can be edited (e.g. `Burpees` / `15 Wdh.`).

Profiles can be created, renamed, deep-duplicated and deleted. Duplicating generates fresh IDs for every child entry so Room keys remain unique. Deleting a profile does not alter past training history.

## Workout modes

- **Runden** – fixed number of rounds.
- **Distanz** – generate rounds until the requested run distance is reached. 0 m rounds can still appear and do not count toward the distance.
- **AMRAP Zeit** – fixed time. When time expires during a round, finish that round and tap once more to complete.
- **AMRAP offen** – continue until manually stopped.

## Controlled randomness

Session generation uses adaptive weighted randomness instead of a rigid rotation:

- configured weights remain the long-term preference
- after an element is selected, its effective weight drops to 25%
- every skipped round restores 7.5 percentage points of that multiplier until the base weight is reached again
- direct repeats are therefore possible, but deliberately less likely
- active real obstacles and route loads are guaranteed to appear at least once when the workout contains enough rounds
- AMRAP sessions apply that coverage early in the generated buffer
- `0 m + Keine + kein Hindernis` is rejected and automatically replaced with a meaningful element

This gives the generator controlled randomness: unpredictable enough to feel like an OCR, without accidentally ignoring configured elements or producing empty rounds.

## Room persistence

Version 0.6 replaces JSON-in-SharedPreferences persistence with **Room**.

Tables:

- `profiles`
- `distances`
- `workout_items`
- `workouts`
- `workout_rounds`
- `app_settings`

Details:

- profile children use foreign keys with cascade delete
- workout history stores snapshots of names/details, so later profile edits do not rewrite historical workouts
- historical rounds are related to their workout with cascade delete
- selected profile is stored in the database as an app setting
- DAO reads use Room relations/transactions rather than N+1 UI-side queries
- profile/history writes are serialized with a coroutine `Mutex`
- newest **500 workouts** are retained
- individual records or all history can be deleted
- Room schema export is enabled under `app/schemas` for future migration testing

### Upgrade from MVP 0.5

Was a one-time JSON-in-SharedPreferences → Room import for existing installs. Removed in 0.7: the app has never shipped, so there was no installed base and nothing left to migrate. `AppRepository.initialize()` now only seeds the default profile on first launch.

Database version is currently **1**. Future schema changes should add explicit Room migrations; production upgrades should not use destructive migration.

## Garmin

For now: record the parallel activity on Garmin as **Trail Run**, just like an OCR race.

MeiOCRWorkout stores exact session and round-change timestamps. A later FIT-import feature can align Garmin HR/pace/distance with the locally stored rounds without requiring live Garmin connectivity.

## Build

Open this project in Android Studio and sync Gradle.

- Android Gradle Plugin 9.3.1
- Kotlin 2.4.20
- KSP 2.3.10
- Room 2.8.5
- Compose BOM 2026.09.00
- compile/target SDK 37
- min SDK 28

A Gradle wrapper binary is not bundled; Android Studio can generate/use the wrapper.

## Package-Struktur

Der Kotlin-Code ist thematisch gegliedert:

- `ui/screens` – Compose-Screens
- `ui` – UI-nahe Hilfen und Strings
- `presentation` – ViewModels und UI-State-Steuerung
- `domain/model` – fachliche Modelle
- `domain/generator` – Trainingsgenerator und Generatorregeln
- `data/local` – Room-Datenbank, Entities und DAOs
- `data/repository` – Repository-Interfaces und Implementierungen
- `util` – allgemeine Formatierungs- und Android-Hilfen
- Root-Package – App-Einstieg (`MainActivity`)
