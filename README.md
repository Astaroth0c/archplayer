# ArchPlayer

Player universal de vídeo para TV Box (Android 7, chipsets Rockchip), Android (celulares), Windows, Linux, Samsung Tizen e LG webOS.

```
archplayer/
├── shared/        Núcleo multiuso (Kotlin/JVM): parser de interfaces TVBox, cliente
│                  Xtream Codes (lógica XCIPTV), parser M3U e guia XMLTV (EPG)
├── androidApp/    APK Android: celular (LAUNCHER) + TV Box (LEANBACK_LAUNCHER),
│                  player Media3/ExoPlayer com HLS e DASH, minSdk 24 (Android 7)
├── desktop/       Windows e Linux via Compose Desktop (deb/rpm/exe/msi)
├── tv-web/        App web único empacotado para Samsung Tizen (.wgt) e LG webOS (.ipk)
└── scripts/       Empacotamento das TVs Samsung/LG
```

## Estado atual

Estrutura funcional de partida (fase 1 do roadmap):

- Parser das interfaces TVBox (`sites`/`parses`/`lives`/`flags`/`spider`/`doh`), tolerante aos formatos "sujos" encontrados nas coleções públicas (campos numéricos como string, `style` como string `'{type, ratio}'`, lives inline ou txt).
- Lógica XCIPTV: cliente Xtream Codes `player_api.php` (`handshake`, categorias, `get_live_streams`, `get_vod_streams`, `get_series`, `get_series_info`, `get_vod_info`), URL builders para `live|movie|series`, `get.php` m3u_plus e `xmltv.php`.
- Parsers M3U / M3U+ e XMLTV para EPG.
- APK Android com launcher duplo (celular e TV Leanback), player HLS/DASH via Media3.
- App desktop (Windows/Linux) e app web para Tizen/webOS com hls.js.

## Compilar

Requisitos: JDK 17 (Android Studio ou linha de comando), Android SDK 35 para o APK.

```bash
# Testes do núcleo
./gradlew :shared:test

# APK (celular + TV box)
./gradlew :androidApp:assembleDebug

# Windows / Linux (instalador nativo)
./gradlew :desktop:packageDistributionForCurrentOS

# Samsung Tizen (.wgt) e LG webOS (.ipk)
./scripts/package-tizen.sh
./scripts/package-webos.sh
```

## Roadmap

Veja [docs/ROADMAP.md](docs/ROADMAP.md) e [docs/ARQUITETURA.md](docs/ARQUITETURA.md).
