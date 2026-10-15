#!/usr/bin/env bash
# Print the SHA-256 digest (lowercase hex) of an APK's signing certificate, after verifying its signature.
# Usage: scripts/apk_cert.sh APK   (needs ANDROID_HOME; the diagnostics go to stderr)
set -euo pipefail
apk="$1"
apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)apksigner"
echo "apksigner: $apksigner" >&2
certs="$("$apksigner" verify --print-certs "$apk" 2>&1)" || { echo "$certs" >&2; exit 1; }
echo "$certs" >&2
cert="$(sed -n 's/^Signer.*certificate SHA-256 digest: //p' <<< "$certs" | head -1)"
if [ -z "$cert" ]; then
  # Fall back to the JDK's reading of the same certificate.
  printed="$(keytool -printcert -jarfile "$apk")"
  echo "$printed" >&2
  cert="$(sed -n 's/^[[:space:]]*SHA256: //p' <<< "$printed" | head -1 | tr -d ':' | tr 'A-F' 'a-f')"
fi
if [ -z "$cert" ]; then
  echo "Could not read the signing certificate digest of $apk" >&2
  exit 1
fi
echo "$cert"
