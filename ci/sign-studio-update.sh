#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 3 ]]; then
    echo "Usage: $0 unsigned.apk owner-release.p12 signed.apk" >&2
    exit 2
fi

unsigned_apk=$1
owner_keystore=$2
signed_apk=$3
: "${TDPS_RELEASE_PASSWORD:?Set the password for the private owner keystore}"
: "${TDPS_BUILD_TOOLS:?Set the Android SDK Build-Tools directory}"

legacy_fingerprint=c4697509e1237ec43a799eaeca5e924401b5766c75ced7c79ba4675828d0d29e
owner_alias=${TDPS_RELEASE_ALIAS:-tablodecori-release}
repo_directory=$(cd "$(dirname "$0")/.." && pwd)
signer="$TDPS_BUILD_TOOLS/apksigner"
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

owner_fingerprint=$(
    keytool -exportcert -alias "$owner_alias" -keystore "$owner_keystore" \
        -storepass:env TDPS_RELEASE_PASSWORD |
        openssl x509 -inform DER -noout -fingerprint -sha256 |
        cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]'
)
if [[ ! $owner_fingerprint =~ ^[a-f0-9]{64}$ || $owner_fingerprint == "$legacy_fingerprint" ]]; then
    echo "A distinct private owner signing key is required." >&2
    exit 1
fi
if [[ -n ${TDPS_RELEASE_CERT_SHA256:-} && $owner_fingerprint != "$TDPS_RELEASE_CERT_SHA256" ]]; then
    echo "The owner keystore does not match the configured release certificate." >&2
    exit 1
fi

"$TDPS_BUILD_TOOLS/aapt" dump badging "$unsigned_apk" > "$scratch/unsigned-badging.txt"
grep -Fq "package: name='com.tablodecori.app' versionCode='13' versionName='1.8.2'" "$scratch/unsigned-badging.txt"
grep -Fxq "sdkVersion:'28'" "$scratch/unsigned-badging.txt"
if grep -q '^application-debuggable' "$scratch/unsigned-badging.txt"; then
    echo "The APK must be a release build." >&2
    exit 1
fi

base64 -d "$repo_directory/ci/v2/stable-debug-keystore.b64" > "$scratch/legacy.keystore"
"$signer" rotate --out "$scratch/rotation.lineage" \
    --old-signer --ks "$scratch/legacy.keystore" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --set-installed-data true --set-shared-uid false --set-permission false \
    --set-rollback false --set-auth false \
    --new-signer --ks "$owner_keystore" --ks-key-alias "$owner_alias" \
    --ks-pass env:TDPS_RELEASE_PASSWORD --key-pass env:TDPS_RELEASE_PASSWORD

"$TDPS_BUILD_TOOLS/zipalign" -P 16 -f 4 "$unsigned_apk" "$scratch/aligned.apk"
"$signer" sign --min-sdk-version 28 --rotation-min-sdk-version 28 \
    --debuggable-apk-permitted false --v1-signing-enabled false \
    --v2-signing-enabled false --v3-signing-enabled true --v4-signing-enabled false \
    --lineage "$scratch/rotation.lineage" --ks "$owner_keystore" \
    --ks-key-alias "$owner_alias" --ks-pass env:TDPS_RELEASE_PASSWORD \
    --key-pass env:TDPS_RELEASE_PASSWORD --out "$scratch/signed.apk" "$scratch/aligned.apk"

"$signer" verify --min-sdk-version 28 --max-sdk-version 28 --verbose --print-certs \
    "$scratch/signed.apk" > "$scratch/android9.txt"
"$signer" verify --min-sdk-version 29 --verbose --print-certs \
    "$scratch/signed.apk" > "$scratch/newer-android.txt"
for verification in "$scratch/android9.txt" "$scratch/newer-android.txt"; do
    grep -Fxq "Signer #1 certificate SHA-256 digest: $owner_fingerprint" "$verification"
    grep -Fxq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$verification"
done

"$signer" lineage --in "$scratch/signed.apk" --print-certs --verbose > "$scratch/lineage.txt"
grep -Fqi "$legacy_fingerprint" "$scratch/lineage.txt"
grep -Fqi "$owner_fingerprint" "$scratch/lineage.txt"
legacy_caps=$(sed -n '/Signer #1/,/Signer #2/p' "$scratch/lineage.txt")
grep -Eqi 'Has installed data capability[[:space:]]*:[[:space:]]*true$' <<< "$legacy_caps"
grep -Eqi 'Has rollback capability[[:space:]]*:[[:space:]]*false$' <<< "$legacy_caps"
grep -Eqi 'Has shared UID capability[[:space:]]*:[[:space:]]*false$' <<< "$legacy_caps"
grep -Eqi 'Has permission capability[[:space:]]*:[[:space:]]*false$' <<< "$legacy_caps"
grep -Eqi 'Has auth capability[[:space:]]*:[[:space:]]*false$' <<< "$legacy_caps"

mkdir -p "$(dirname "$signed_apk")"
mv "$scratch/signed.apk" "$signed_apk"
echo "Verified private signer $owner_fingerprint and migration lineage from 1.7.1 on Android 9+."
