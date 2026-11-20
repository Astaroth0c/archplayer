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

if [ -f "$ROOT/tv-web/assets/icon.png.b64" ]; then
  base64 -d "$ROOT/tv-web/assets/icon.png.b64" > "$DIST/icon.png"
fi

if command -v base64 >/dev/null 2>&1 && [ ! -s "$DIST/icon.png" ]; then
  echo "Aviso: icon.png não pôde ser gerado; gere manualmente (512x512)."
fi

cd "$DIST"
if command -v zip >/dev/null 2>&1; then
  zip -qr "$ROOT/build/ArchPlayer-tizen.wgt" .
  echo "OK: $ROOT/build/ArchPlayer-tizen.wgt"
else
  echo "Instale 'zip' ou use o Tizen Studio (Device Manager + Certificate Manager + Package)."
  exit 1
fi
