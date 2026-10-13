#!/bin/sh
# Builds the signed release APK. The keystore lives outside the repo (hermes.signing.storeFile in
# ~/.gradle/gradle.properties); its password is read from the macOS Keychain item
# "hermes-mobile-signing" and only exported to this Gradle run.
set -eu
cd "$(dirname "$0")/.."
HERMES_SIGNING_PASSWORD="$(security find-generic-password -s hermes-mobile-signing -w)"
export HERMES_SIGNING_PASSWORD
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export ANDROID_HOME
./gradlew :app:assembleRelease "$@"
APK=app/build/outputs/apk/release/app-release.apk
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)apksigner"
"$APKSIGNER" verify --print-certs "$APK" | grep -E "Signer #1 certificate (DN|SHA-256)"
echo "Signed: $APK"
