# EcoRota

EcoRota is a free, offline-first Android app that shows where your driving wastes fuel. It records trips from GPS and motion sensors, scores each trip from 0 to 100, explains which behaviors cost you points, and estimates the fuel and money you could save.

No account. No analytics. No server. Trip data stays in a local database on your phone unless you explicitly export it.

## Features

### Free tier

- Automatic trip detection with an optional manual start and stop
- Driving style analysis from GPS and the accelerometer
- Detection of hard acceleration, hard braking, sharp cornering, overspeed, and unnecessary idling
- Explainable 0 to 100 efficiency score per trip
- Trip history with route map (OpenStreetMap), distance, duration, average and maximum speed
- Per-trip event list
- Free fuel estimate based on your vehicle's consumption and local fuel price
- Driving tips selected from your most frequent inefficiencies
- Dark mode, English and Brazilian Portuguese

### Premium, one-time unlock

- Advanced statistics and weekly trends
- Multiple vehicles
- Severity detail for every event
- CSV and GPX export

Premium is a single in-app purchase. There is no subscription. The community build (the `libre` flavor) has no billing library and unlocks everything locally.

## Privacy

All processing and storage happen on the device. The app has no account system, no analytics SDK, and no backend.

The only network access is optional map tile download. The app asks for consent before requesting any tile from an OpenStreetMap server; until you allow it, maps draw the recorded route on an empty background using cached tiles only. You can revoke consent at any time in Settings.

The Play build uses Google Play services for location and for the optional one-time premium purchase (Play Billing). EcoRota itself never uploads trips, scores, or location history. Google's own privacy policy applies to those platform services. The community (`libre`) build has no Play services or Play Billing dependency; it uses Android's platform location provider.

Android cloud backup is disabled for EcoRota. Trips are personal location data, so they are not copied to Google Drive or transferred automatically.

Read [PRIVACY.md](PRIVACY.md) for the full statement.

## Build

Requirements:

- JDK 17 or newer
- Android SDK with platform 36 and build-tools 35.0.0
- Android Studio (latest stable) or the Gradle wrapper

```bash
git clone https://github.com/bashln/ecorota.git
cd ecorota

# Debug build of the Play flavor
./gradlew :app:assemblePlayDebug

# Debug build of the community flavor (no billing)
./gradlew :app:assembleLibreDebug

# Unit tests for the scoring engine and data layer
./gradlew :core:domain:test :app:testPlayDebugUnitTest

# Static analysis
./gradlew :app:lintPlayDebug :app:lintLibreDebug
```

The APK is written to `app/build/outputs/apk/<flavor>/<buildType>/`. Debug builds are for local testing only.

## Releases

Release artifacts are built by GitHub Actions, never on a development machine. Pushing a tag in the form `vMAJOR.MINOR.PATCH` runs [.github/workflows/release.yml](.github/workflows/release.yml), which tests, lints, signs and publishes a GitHub release with:

- `ecorota-play-<version>.aab` for Google Play
- `ecorota-play-<version>.apk` and `ecorota-libre-<version>.apk` for direct install
- R8 mapping files and `SHA256SUMS.txt`

Signing credentials live only in repository secrets. See [docs/RELEASING.md](docs/RELEASING.md) for the one-time setup, the tag workflow and the version code scheme.

For a local signed release, create `keystore.properties` in the repository root (gitignored) with:

```properties
storeFile=../ecorota-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`app/build.gradle.kts` picks it up automatically. CI instead provides the same values through `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Without either source, release builds are unsigned. Never commit keystores or passwords.

### Product flavors

| Flavor | Application ID | Billing | Premium |
| --- | --- | --- | --- |
| `play` | `app.ecorota` | Google Play Billing | One-time purchase |
| `libre` | `app.ecorota.libre` | None | Unlocked locally |

The `libre` flavor is intended for F-Droid and self-builds. It has no Google Play services dependency at all: billing is absent and location uses the platform `LocationManager`. The Play flavor uses the Fused Location Provider with a platform fallback for devices without Play services.

To test billing, create these in-app products in Play Console and add your account as a license tester:

| Product ID | Type | Purpose |
| --- | --- | --- |
| `premium_unlock` | One-time, non-consumable | Unlocks premium features |
| `tip_coffee` | Consumable | Optional tip |

Without Play, use the `libre` flavor. The tip is optional and unlocks nothing; it is separate from Premium.

## How the score works

The score combines five behavior components with a fuel-waste component:

| Component | Default weight | What it measures |
| --- | --- | --- |
| Braking | 25% | Severity-weighted hard braking per 10 km |
| Acceleration | 20% | Severity-weighted hard acceleration per 10 km |
| Speed | 20% | Share of moving time above the efficient speed |
| Idling | 20% | Share of trip time spent idling unnecessarily |
| Cornering | 15% | Severity-weighted sharp cornering per 10 km |

The behavior score is blended with a fuel-waste sub-score, so a trip that wastes more fuel scores lower even when the event count is similar. Every threshold, weight, and physics constant is stored in `ScoringConfig` and can be changed in Settings.

The app does not treat the score as a black box. The trip detail screen shows how many points each category removed and which events caused it.

[SCORING.md](docs/SCORING.md) documents the formulas, the fuel physics, and the tuning parameters.

## Project layout

```
app/                         Android app module
  src/main/java/app/ecorota/
    data/                    Room database, repositories, DataStore settings
    tracking/                Foreground service, location, sensor fusion, auto-detection
    billing/                 Play Billing and the no-op community implementation
    export/                  CSV and GPX writers
    ui/                      Compose screens, navigation, theme
core/domain/                 Pure Kotlin models, analyzer, scorer, statistics
  src/test/                  Unit tests for the engine
docs/                        Architecture and scoring documentation
```

Dependencies point one way: `app` depends on `core:domain`. The domain module has no Android dependency, so the scoring engine runs in plain JVM tests and can be reused by other clients.

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for the data flow and lifecycle details.

## Permissions

| Permission | Why |
| --- | --- |
| Fine and coarse location | Record the route, speed, and stops |
| Background location | Optional auto-detection while the app is not open |
| Foreground service, location type | Keep recording reliable with a visible notification |
| Notifications | Show the ongoing recording notification |
| Internet | Optional OpenStreetMap tile download, off by default |

Location is read only while a trip is recorded or auto-detection is enabled. The app never requests location in the background without the foreground service and its notification.

## Play Store compliance notes

Before publishing:

1. Complete the Data safety form and declare that trip data is collected locally and not shared. Location is processed on device.
2. Declare background location access in Play Console and provide an in-app prominent disclosure before requesting it. The onboarding flow includes that disclosure.
3. Declare the foreground service location use. Recording requires an active trip or auto-detection.
4. Publish the privacy policy URL. [PRIVACY.md](PRIVACY.md) is ready to host.
5. Configure the `premium_unlock` one-time product, the optional `tip_coffee` consumable, and the license testers.
6. Keep the target SDK within Play's current requirement. This project targets API 36.

## Contributing

Contributions are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md). In short:

- Open an issue before a large change.
- Keep the domain module free of Android imports.
- Add or update unit tests for scoring changes.
- Update `docs/SCORING.md` when you change a formula or a default.

## Support

The app itself is free. The Play build offers a one-time premium purchase for advanced features plus an optional consumable tip in Settings. The tip unlocks nothing. The community build has neither. There are no ads, and no ads will ever appear while driving.

## Roadmap

The MVP is deliberately small. Planned work that does not compromise the offline design:

- Optional encrypted cloud backup, strictly opt-in and off by default
- Offline map packages (MBTiles) so maps work without any tile server
- OBD-II fuel measurements for users who want calibrated rather than estimated consumption
- Monthly trend comparison alongside the weekly view
- Android Auto and Wear companions

## License

MIT. See [LICENSE](LICENSE).

Map data is © OpenStreetMap contributors, available under the Open Database License. EcoRota displays the required attribution on every map.
