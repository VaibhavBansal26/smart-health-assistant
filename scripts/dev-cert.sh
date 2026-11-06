#!/usr/bin/env bash
# Creates a self-signed certificate (PKCS12 keystore) so you can run the backend over HTTPS locally.
# FOR LOCAL TESTING ONLY: browsers will warn about it. In production use a real certificate, or terminate TLS at a
# reverse proxy / load balancer (then set REQUIRE_HTTPS=true and FORWARD_HEADERS_STRATEGY=framework instead).
#
# Usage:  scripts/dev-cert.sh [output-dir]      (default: .certs, which is git-ignored)
set -euo pipefail

OUT_DIR="${1:-.certs}"
STORE="$OUT_DIR/dev-keystore.p12"
PASS_FILE="$OUT_DIR/keystore-password.txt"

mkdir -p "$OUT_DIR"
[ -e "$STORE" ] && { echo "$STORE already exists; delete it first to regenerate." >&2; exit 1; }

PASS="$(openssl rand -base64 18)"
keytool -genkeypair -alias dev -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore "$STORE" -validity 365 -dname "CN=localhost" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" -storepass "$PASS" -keypass "$PASS" >/dev/null 2>&1

umask 077
printf '%s' "$PASS" > "$PASS_FILE"

cat <<MSG
Created $STORE (valid 365 days). Its password is in $PASS_FILE.

Add this to your .env, then restart the backend (it will serve https on SERVER_PORT):
  SSL_ENABLED=true
  SSL_KEY_STORE=$(cd "$OUT_DIR" && pwd)/dev-keystore.p12
  SSL_KEY_STORE_PASSWORD=<contents of $PASS_FILE>
  COOKIE_SECURE=true
  CORS_ORIGINS=http://localhost:4200
and set "apiUrl" in frontend/public/config.json to https://localhost:8080/api
(open https://localhost:8080 once in the browser and accept the warning so the app can call it).
MSG
