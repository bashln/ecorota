# Contributing to EcoRota

EcoRota is a small, offline-first Android app. Contributions that keep it small, testable, and privacy-respecting are welcome.

## Before you start

- Open an issue for anything larger than a bug fix or a translation. Describe the problem and the proposed behavior.
- Do not add a backend, analytics, crash reporting, or an ad SDK. These are against the project's design.
- Do not add a dependency without explaining in the pull request why the Android or Kotlin standard library is insufficient.

## Development setup

1. Install JDK 17 or newer and Android SDK platform 36 with build-tools 35.0.0.
2. Clone the repository and open it in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleLibreDebug
./gradlew :core:domain:test :app:testPlayDebugUnitTest
./gradlew :app:lintPlayDebug
```

3. Use the `libre` flavor for UI work that does not involve billing. It needs no Play services or product configuration.

## Code guidelines

- `core:domain` must stay pure Kotlin. No Android imports, no `Context`, no `Log`.
- Keep user-visible text in `res/values/strings.xml` and `res/values-pt-rBR/strings.xml`. Both locales must be updated in the same pull request.
- Comments explain why, not what. If a formula is not obvious, reference the section in `docs/SCORING.md`.
- Public engine APIs get a short KDoc. Internal helpers do not need one.
- Use structured concurrency. Do not use `GlobalScope`, and do not block the main thread.
- Prefer immutable data classes and explicit state. Avoid boolean soup in the tracking service.
- Keep the free tier fully useful. A new premium gate needs a product decision in an issue first.

## Tests

- Scoring, detection, statistics, and physics changes require unit tests in `core:domain`. Assert concrete values or tight properties; a test that passes for any implementation is not a test.
- Persistence codecs and export formats require unit tests in `:app`.
- Update `docs/SCORING.md` when a formula, threshold, or default changes. Add a test that pins the new behavior.
- Do not claim manual testing you did not perform. If a change needs a device (location, sensors, foreground service), list the exact steps you ran and the device or emulator.

## Localization

- English is the default `values/` locale. Brazilian Portuguese lives in `values-pt-rBR/`.
- Use plain language. Avoid literal translations of idioms.
- Keep placeholders and their order consistent across locales.

## Commits and pull requests

- One logical change per commit. Conventional prefixes are appreciated: `feat:`, `fix:`, `test:`, `docs:`, `chore:`.
- The pull request description states the user-visible change, how it was verified, and any residual risk.
- CI must pass: domain tests, app unit tests, debug assembly, and lint.
- Squash fixup commits before review when practical.

## Reporting bugs

Include the Android version, device model, app version and flavor, and the steps that reproduce the problem. For scoring complaints, include the trip's score breakdown and the events if you are comfortable sharing them. Never attach a full location history unless the issue truly needs it.

## Security and privacy

- Never commit API keys, keystores, signing passwords, or personal location data.
- Report a privacy or security issue privately to the maintainers rather than opening a public issue with sensitive details.
- Any new permission needs a justification in the pull request and an update to the permissions table in the README.

## License

By contributing, you agree that your contribution is licensed under the MIT License.
