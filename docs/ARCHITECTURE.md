# Architecture

EcoRota is a two-module Android project with a strict dependency direction.

```
:app  ->  :core:domain
```

`:core:domain` is pure Kotlin. It contains the models, the streaming analyzer, the scorer, the fuel estimate, the auto trip detector, statistics, and the localization-neutral tip identifiers. It has no Android or third-party runtime dependency beyond Kotlin serialization for the configuration codec. This is what makes the engine testable in a plain JVM and reusable by a future CLI or server-side analysis tool.

`:app` contains Android concerns: Room, DataStore, location, sensors, the foreground service, billing, export, and Compose UI.

## Runtime data flow

```
FusedLocationProvider ─┐
                       ├─> TelemetrySample ─> TripAnalyzer ─> TripAnalysis
SensorFusion ──────────┘                                      │
                                                              v
                                                     TripScorer (+ VehicleProfile)
                                                              │
                                          ┌───────────────────┴──────────────────┐
                                          v                                      v
                                  LiveTripState (HUD)                    TripRecord -> Room
```

1. `TripTrackingService` runs as a location foreground service. In monitoring mode it uses low-power fixes to decide when a drive starts. In recording mode it requests balanced-power fixes every few seconds.
2. `SensorFusion` low-pass filters gravity out of the accelerometer and projects linear acceleration onto the vehicle's forward and lateral axes, using the GPS bearing as heading. If no bearing is available, the analyzer derives acceleration from GPS speed changes.
3. `TripAnalyzer` accumulates distance, moving and idle time, speed samples, a thinned route, and detected events. It is a state machine, not a batch job, so the same object serves the live HUD and the final record.
4. `TripScorer` turns the analysis into a `ScoreBreakdown`, a `FuelEstimate`, and scored `DrivingEvent`s. It is pure and deterministic for a given analysis, vehicle, and configuration.
5. On stop, the service discards trivial recordings, then writes a `TripRecord` through `TripRepository`. Route points are encoded with the Google polyline algorithm and carry one timestamped speed sample each, so map rendering and GPX export reuse the same exact times.

## Package responsibilities

| Package | Responsibility |
| --- | --- |
| `core.model` | Coordinates, telemetry, events, score breakdown, fuel estimate, trip record, settings |
| `core.scoring` | `TripAnalyzer`, `TripScorer`, `ScoringConfig`, configuration codec |
| `core.detection` | `TripDetector`, the pure start/stop state machine |
| `core.analysis` | Trip statistics and tip selection |
| `app.data.local` | Room entities, DAOs, database, mappers |
| `app.data.repository` | Repository implementations for trips and vehicles |
| `app.data.settings` | DataStore-backed `SettingsRepository` |
| `app.tracking` | Foreground service, location source, sensor fusion, controller, retention worker |
| `app.billing` | Play Billing client and the no-op community implementation |
| `app.export` | CSV and GPX writers |
| `app.ui` | Compose screens, view models, navigation, theme |
| `app.di` | Manual dependency container created by `EcoRotaApp` |

There is no dependency injection framework. The graph is small and constructed once in `AppContainer`, which keeps startup predictable and avoids reflection.

## Storage

Room database `ecorota.db`, version 1.

- `trips` holds one row per trip, including scalar statistics, the encoded route, and the encoded speed series. Score components are denormalized into columns so the history list can render without recomputing.
- `driving_events` holds scored events with a cascading foreign key to `trips`.
- `vehicles` holds consumption and fuel price. The free tier uses one vehicle; premium adds more.

DataStore holds user preferences in two files: `settings` for preferences and scoring configuration, and `premium` for the entitlement.

The database is excluded from Android backup and device transfer. Deletion is immediate and local. The retention worker removes trips older than the user's retention window when one is configured.

## Tracking lifecycle

```
Idle ──auto start / manual start──> Recording ──manual stop / auto stop──> Scored
 ^                                      │
 └──────────── monitoring <────────────┘
```

- Monitoring is optional and requires background location permission. It samples low-power location and feeds `TripDetector`. After a drive is confirmed, it switches the same service to recording.
- Recording updates the notification with distance and live score. The foreground location service keeps the process alive while fixes arrive.
- Sensors are registered only during recording. This is the main battery optimization: no sensor sampling while parked, and no high-accuracy GPS while monitoring.
- If the process is killed, a sticky restart resumes monitoring, not recording. A partially recorded trip is lost rather than guessed, which avoids storing a route with a silent gap.

## Score and fuel model

The model is intentionally simple and documented. It has no machine learning and no hidden coefficients. `ScoringConfig` is serialized to DataStore, so a user or contributor can change thresholds and physics constants without a rebuild. Defaults live in `core/domain/src/main/kotlin/app/ecorota/core/scoring/ScoringConfig.kt`.

See [SCORING.md](SCORING.md) for the equations and worked examples.

## Premium gating

Premium is a single boolean entitlement with a source. The UI checks `Entitlement.canUse(feature)` for each gated surface. The data layer does not enforce gating, so advanced queries remain available to the community build and to tests.

The `play` flavor uses `PlayBillingProvider`. The `libre` flavor uses `NoOpBillingProvider` and grants the unlock at startup. Both write through `PremiumRepository`, so the rest of the app has one source of truth.

There is no server receipt validation. A one-time purchase in Play is verified on-device by the Play Billing library. Anti-piracy hardening would require a backend, which contradicts the offline-first design.

## Maps

osmdroid renders OpenStreetMap tiles. Tiles are cached on disk. Any network download is gated by an explicit consent setting; without consent, the map still draws the route, cached tiles, and attribution, but never contacts a tile server. A future offline map picker can plug into the same layer through an MBTiles archive without changing the trip screens.

## Location providers

The play flavor uses the Fused Location Provider and falls back to the platform `LocationManager` when Play services is unavailable. The libre flavor compiles only the platform provider, so its APK contains no Google location code. Both share the same two profiles: a low-power monitor and a balanced-power recording stream.

Automatic detection is restored after a reboot by a boot receiver, but only when the user enabled it and still granted all-the-time location.

## Testing strategy

- `core:domain` unit tests cover event detection, scoring math, fuel physics, configuration handling, polyline coding, statistics, and auto-detection. These run on the JVM with no Android SDK.
- `:app` unit tests cover persistence codecs and export formats.
- Instrumented tests are not part of the MVP. The tracking service needs a device or emulator with mocked location, and the project documents manual test steps instead of claiming automated coverage it does not have.

CI runs the domain tests, app unit tests, a debug assembly, and lint on every pull request.
