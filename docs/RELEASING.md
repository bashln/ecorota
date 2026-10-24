# Releasing EcoRota

Releases are built and signed by GitHub Actions. The development machine is not
part of the release path: a tag push is the only trigger.

Workflow: [`.github/workflows/release.yml`](../.github/workflows/release.yml)

## Tag format

Tags must be `vMAJOR.MINOR.PATCH`, for example `v0.2.0`. The leading `v` is
removed for `versionName`.

`versionCode` is derived from the same tag:

```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

| Tag | versionName | versionCode |
| --- | --- | --- |
| `v0.1.0` | `0.1.0` | 100 |
| `v0.2.0` | `0.2.0` | 200 |
| `v1.0.0` | `1.0.0` | 10000 |

`MINOR` and `PATCH` must be between 0 and 99. If a component needs to grow past
that, change the formula in the workflow and in this document before tagging.

## One-time setup: upload key

The Play flavor is signed with an upload key. Google Play App Signing re-signs
the bundle with the app signing key, so the upload key never has to leave the
CI secrets. The `libre` APK is signed with the same key for consistent updates.

Generate the key once, on a trusted machine:

```bash
keytool -genkeypair \
  -v \
  -keystore ecorota-upload.jks \
  -alias ecorota-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Back it up twice: the password manager and an offline copy. Losing the upload
key blocks updates until Google Play resets it, so treat the file and the
passwords as permanent infrastructure.

Encode the keystore for the secret:

```bash
# Linux
base64 -w0 ecorota-upload.jks > ecorota-upload.jks.b64

# macOS
base64 -i ecorota-upload.jks -o ecorota-upload.jks.b64
```

Add the four repository secrets (GitHub UI: Settings > Secrets and variables >
Actions, or the CLI):

```bash
gh secret set RELEASE_KEYSTORE_BASE64 --repo bashln/ecorota < ecorota-upload.jks.b64
gh secret set RELEASE_KEYSTORE_PASSWORD --repo bashln/ecorota
gh secret set RELEASE_KEY_ALIAS --repo bashln/ecorota
gh secret set RELEASE_KEY_PASSWORD --repo bashln/ecorota
```

Check the names, never the values:

```bash
gh secret list --repo bashln/ecorota
```

Delete the base64 file after uploading it.

The workflow fails with a clear error before building when any of the four
secrets is missing. It never prints secret values.

## Publishing a release

1. Merge the release content into `main` and make sure the merge commit has a
   green `Android` workflow run.
2. Tag that commit and push the tag:

```bash
git switch main
git pull --ff-only
git tag -a v0.2.0 -m "EcoRota 0.2.0"
git push origin v0.2.0
```

3. The `Release` workflow runs tests, lint, R8 and signing, then creates the
   GitHub release named `EcoRota <version>` with generated release notes.

Re-running the workflow for an existing tag replaces the assets in the release
(`gh release upload --clobber`); it does not create a second release.

## Assets

| Asset | Purpose |
| --- | --- |
| `ecorota-play-<version>.aab` | Upload to Google Play Console |
| `ecorota-play-<version>.apk` | Direct install of the Play flavor |
| `ecorota-libre-<version>.apk` | Community build, premium unlocked locally |
| `ecorota-play-<version>-mapping.txt` | R8 mapping for Play crash reports |
| `ecorota-libre-<version>-mapping.txt` | R8 mapping for the community build |
| `SHA256SUMS.txt` | Checksums for every asset |

Verify a download:

```bash
sha256sum -c SHA256SUMS.txt
```

The workflow also verifies the APK signatures with `apksigner` and the AAB with
`jarsigner` before publishing.

## Signing behavior

`app/build.gradle.kts` reads signing material from the environment first, then
from a gitignored `keystore.properties` at the repository root:

| Environment variable | `keystore.properties` key |
| --- | --- |
| `RELEASE_KEYSTORE_PATH` | `storeFile` |
| `RELEASE_KEYSTORE_PASSWORD` | `storePassword` |
| `RELEASE_KEY_ALIAS` | `keyAlias` |
| `RELEASE_KEY_PASSWORD` | `keyPassword` |

Without a complete set, release builds are unsigned. That is acceptable for the
pull request workflow, which only checks that R8 and packaging work. The release
workflow treats an unsigned build as a failure.

`-PappVersionName` and `-PappVersionCode` override the development defaults; the
release workflow sets both from the tag.

## Play Console

The Play flavor depends on two products:

| Product ID | Type | Purpose |
| --- | --- | --- |
| `premium_unlock` | One-time, non-consumable | Unlocks premium |
| `tip_coffee` | Consumable | Optional tip |

Upload the AAB to an internal track first. Play App Signing must be enabled so
the upload key above is only used to authenticate the upload.

## Troubleshooting

- **`Release signing not configured`** — add the four secrets listed above.
- **`Invalid release tag`** — tag must match `vMAJOR.MINOR.PATCH`.
- **`MINOR and PATCH must be 0..99`** — bump `MAJOR` or adjust the versionCode
  formula in the workflow.
- **Signature verification fails** — the keystore secret and the password,
  alias or key password do not belong together. Fix the secrets; do not change
  the signing configuration to skip verification.
