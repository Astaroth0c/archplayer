#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/build/webos"

rm -rf "$DIST"
mkdir -p "$DIST"

cp "$ROOT/tv-web/index.html" "$DIST/"
cp -r "$ROOT/tv-web/css" "$DIST/"
cp -r "$ROOT/tv-web/js" "$DIST/"
cp "$ROOT/tv-web/webos/appinfo.json" "$DIST/"
cp "$ROOT/tv-web/assets/icon80x80.png" "$DIST/" 2>/dev/null || true
cp "$ROOT/tv-web/assets/icon160x160.png" "$DIST/" 2>/dev/null || true

if command -v ares-package >/dev/null 2>&1; then
  ares-package "$DIST" -o "$ROOT/build"
  echo "OK: IPK gerado em $ROOT/build/"
else
  cd "$DIST"
  if command -v zip >/dev/null 2>&1; then
    zip -qr "$ROOT/build/ArchPlayer-webos-unpacked.zip" .
    echo "webOS SDK (ares-package) nao encontrado. Estrutura pronta em $DIST e zip em $ROOT/build/."
  fi
fi
