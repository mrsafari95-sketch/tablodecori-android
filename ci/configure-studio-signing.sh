#!/usr/bin/env bash
# Run from the repository owner's computer with JDK and GitHub CLI installed.
set -euo pipefail
umask 077

repo=mrsafari95-sketch/tablodecori-android
branch=feature/product-studio-sync
release_alias=tablodecori-release
legacy_cert=c4697509e1237ec43a799eaeca5e924401b5766c75ced7c79ba4675828d0d29e

for program in gh keytool openssl; do
    command -v "$program" >/dev/null || { echo "Required program: $program" >&2; exit 1; }
done
gh auth status --hostname github.com >/dev/null 2>&1 || {
    echo "Sign in to GitHub CLI on this computer before running the setup." >&2
    exit 1
}

if [[ -n ${LOCALAPPDATA:-} ]] && command -v cygpath >/dev/null; then
    local_data="$(cygpath -u "$LOCALAPPDATA")"
else
    local_data="${XDG_DATA_HOME:-$HOME/.local/share}"
fi
keystore="${1:-$local_data/Tablodecori/signing/tablodecori-release.p12}"
pin=$(gh variable list --repo "$repo" --json name,value \
    --jq '.[] | select(.name == "TDPS_RELEASE_CERT_SHA256") | .value')
if [[ -n $pin && ! -f $keystore ]]; then
    echo "A release signer is already configured. Supply its local keystore path to preserve update compatibility." >&2
    exit 1
fi

printf 'Keystore destination: %s\nGitHub repository: %s\n' "$keystore" "$repo"
read -r -p 'Type the repository name exactly to confirm saving the signing key to its Actions Secrets: ' target
[[ $target == "$repo" ]] || exit 1
read -r -s -p 'Private keystore password: ' TDPS_RELEASE_PASSWORD
printf '\n'
if [[ ${#TDPS_RELEASE_PASSWORD} -lt 16 ]]; then
    echo "Use a private keystore password of at least 16 characters." >&2
    exit 1
fi
export TDPS_RELEASE_PASSWORD
trap 'unset TDPS_RELEASE_PASSWORD' EXIT

if [[ ! -f $keystore ]]; then
    read -r -s -p 'Confirm the keystore password: ' confirmation
    printf '\n'
    [[ $confirmation == "$TDPS_RELEASE_PASSWORD" ]] || { echo 'Passwords differ.' >&2; exit 1; }
    unset confirmation
    mkdir -p "$(dirname "$keystore")"
    keytool -genkeypair -noprompt -storetype PKCS12 -alias "$release_alias" \
        -keyalg RSA -keysize 3072 -validity 10950 -dname 'CN=Tablodecori Android Release' \
        -keystore "$keystore" -storepass:env TDPS_RELEASE_PASSWORD
fi

fingerprint=$(
    keytool -exportcert -alias "$release_alias" -keystore "$keystore" -storepass:env TDPS_RELEASE_PASSWORD |
        openssl x509 -inform DER -noout -fingerprint -sha256 |
        cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]'
)
if [[ ! $fingerprint =~ ^[a-f0-9]{64}$ || $fingerprint == "$legacy_cert" || ( -n $pin && $pin != "$fingerprint" ) ]]; then
    echo "This keystore cannot replace the configured private release certificate." >&2
    exit 1
fi
if [[ -z $pin ]]; then
    printf '%s' "$fingerprint" | gh variable set TDPS_RELEASE_CERT_SHA256 --repo "$repo"
fi
base64 "$keystore" | tr -d '\r\n' | gh secret set TDPS_RELEASE_KEYSTORE_B64 --repo "$repo"
printf '%s' "$TDPS_RELEASE_PASSWORD" | gh secret set TDPS_RELEASE_PASSWORD --repo "$repo"
unset TDPS_RELEASE_PASSWORD

printf 'The owner key is configured. Keep a protected backup of %s.\n' "$keystore"
gh workflow run build-apk.yml --repo "$repo" --ref "$branch"
