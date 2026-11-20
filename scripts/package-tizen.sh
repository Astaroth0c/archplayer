#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/build/tizen"

rm -rf "$DIST"
mkdir -p "$DIST"

cp "$ROOT/tv-web/index.html" "$DIST/"
cp -r "$ROOT/tv-web/css" "$DIST/"
cp -r "$ROOT/tv-web/js" "$DIST/"
cp "$ROOT/tv-web/tizen/config.xml" "$DIST/"
cp "$ROOT/tv-web/assets/icon.png" "$DIST/icon.png" 2>/dev/null || true

cd "$DIST"
if command -v zip >/dev/null 2>&1; then
  zip -qr "$ROOT/build/ArchPlayer-tizen.wgt" .
  echo "OK: $ROOT/build/ArchPlayer-tizen.wgt"
else
  echo "Instale 'zip' ou use o Tizen Studio (Device Manager + Certificate Manager + Package)."
  exit 1
fi
