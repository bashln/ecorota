#!/usr/bin/env bash
#
# One-time provisioning of the EcoRota release upload key.
#
# Run this as the repository owner from a trusted terminal:
#
#   ./scripts/setup-release-signing.sh
#
# The script creates the keystore, then publishes the four GitHub Actions
# secrets from stdin. It never writes a secret to the repository and never
# passes one as a command-line argument. Back up the keystore and its password
# in Bitwarden and in an offline copy before tagging a release.
#
# If a keystore already exists at the configured path, the script reuses it and
# only republishes the secrets (for example after rotating the GitHub token).
# Set ECOROTA_KEYSTORE_DIR or ECOROTA_KEY_ALIAS to override the defaults.

set -euo pipefail

if [ ! -t 0 ]; then
  echo "Run this script interactively from a terminal." >&2
  exit 1
fi

for tool in keytool gh base64; do
  if ! command -v "$tool" > /dev/null 2>&1; then
    echo "Missing required tool: $tool" >&2
    exit 1
  fi
done

if ! gh auth status > /dev/null 2>&1; then
  echo "The GitHub CLI is not authenticated. Run 'gh auth login' first." >&2
  exit 1
fi

REPO="${ECOROTA_REPO:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
KEYSTORE_DIR="${ECOROTA_KEYSTORE_DIR:-$HOME/.config/ecorota}"
KEYSTORE="$KEYSTORE_DIR/ecorota-upload.jks"
ALIAS="${ECOROTA_KEY_ALIAS:-ecorota-upload}"

trap 'unset KS_PASS 2>/dev/null || true' EXIT

echo "Repository: $REPO"
echo "Keystore:   $KEYSTORE"
echo "Alias:      $ALIAS"
echo

read -rsp "Keystore password (also used as key password): " KS_PASS
echo
if [ -z "$KS_PASS" ]; then
  echo "Password cannot be empty." >&2
  exit 1
fi

read -rsp "Repeat the password: " KS_PASS_CONFIRM
echo
if [ "$KS_PASS" != "$KS_PASS_CONFIRM" ]; then
  echo "Passwords do not match." >&2
  exit 1
fi
unset KS_PASS_CONFIRM
export KS_PASS

if [ -f "$KEYSTORE" ]; then
  echo "Existing keystore found; it will be reused, not replaced."
  if ! keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" \
      -storepass:env KS_PASS > /dev/null 2>&1; then
    echo "The password does not open $KEYSTORE with alias $ALIAS." >&2
    exit 1
  fi
else
  mkdir -p "$KEYSTORE_DIR"
  chmod 700 "$KEYSTORE_DIR"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=EcoRota Upload, O=bashln" \
    -storepass:env KS_PASS \
    -keypass:env KS_PASS
  chmod 600 "$KEYSTORE"
  echo "Created $KEYSTORE"
fi

echo
echo "The next step uploads four secrets to $REPO:"
echo "  RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD,"
echo "  RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"
read -rp "Type publish to continue: " CONFIRM
if [ "$CONFIRM" != "publish" ]; then
  echo "Aborted. Secrets were not changed."
  exit 1
fi

base64 < "$KEYSTORE" | tr -d '\n' \
  | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$KS_PASS" \
  | gh secret set RELEASE_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" \
  | gh secret set RELEASE_KEY_ALIAS --repo "$REPO"
printf '%s' "$KS_PASS" \
  | gh secret set RELEASE_KEY_PASSWORD --repo "$REPO"

unset KS_PASS

echo
echo "Secrets now present:"
gh secret list --repo "$REPO" | grep -E '^RELEASE_' || true
echo
echo "Back up the keystore and its password in Bitwarden and offline."
echo "Do not commit the keystore. It is already covered by .gitignore."
