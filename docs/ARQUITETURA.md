# Arquitetura do ArchPlayer

## Alvos e tecnologia por plataforma

| Alvo | Tecnologia | Empacotamento |
|---|---|---|
| TV Box Android 7 (Rockchip RK322x/RK3328 etc.) e celulares | Kotlin, Compose (UI dupla: Leanback + toque), Media3/ExoPlayer (HLS, DASH, mp4/ts) | APK único `minSdk 24`, launchers `LEANBACK_LAUNCHER` + `LAUNCHER` |
| Windows / Linux | Kotlin + Compose Desktop (JVM) | `Exe/Msi` e `Deb/Rpm` |
| Samsung Tizen | Web app (HTML/JS + hls.js) | `.wgt` (config.xml Tizen 4.0) |
| LG webOS | O mesmo web app | `.ipk` (appinfo.json) |

Por que o núcleo é Kotlin/JVM único (`shared/`): atende Android e Desktop hoje; a migração para Kotlin Multiplatform (iOS/Web futuros) é incremental — os modelos já são `data classes` com `kotlinx.serialization` sem dependências de plataforma.

## Módulos

```
shared/
  tvbox/   TvBoxConfig, Site(type 0/1/3), Parse(type 0..3), Live(type 0/1),
           TvBoxConfigParser (leniente: booleanos "1"/"0", style em string, lives inline/txt)
  xciptv/  XtreamClient (player_api.php): handshake, categorias, live/vod/series,
           series_info (temporadas/episodios), vod_info; URLs live|movie|series,
           get.php m3u_plus, xmltv.php  <- logica XCIPTV
  m3u/     M3uParser (#EXTM3U/#EXTINF, m3u_plus, x-tvg-url)
  xmltv/   XmltvParser (canais + programas, fusos +0000/-0300)
```

## Playback

- Android: Media3 ExoPlayer (HLS/DASH/progressivo). Para streams problematicos das fontes TVBox (ts puros, UA obrigatoria) o `PlayerActivity` aceita `EXTRA_UA`; a fase 2 adiciona backend alternativo (LibVLC/ijkplayer) e o "sniffer" (parses type 3) via WebView.
- Desktop: VLC (vlcj) como backend na fase 2, atras de interface `PlayerEngine`.
- Tizen/webOS: hls.js (MSE) — Tizen 4+ e webOS 4+ tem MSE; fallback `<video src>` nativo para mpeg-ts quando o TV decodifica.

## Fontes

- TVBox config: o usuario informa a URL da interface (colecao propria ou publica). Sites type 3 exigem execucao de spiders (jar via DexClassLoader / JS via QuickJS) — fases 2/3; type 0/1 (Xipe/mac e CMS JSON) vem antes por ser puro HTTP+JSON.
- Xtream/XCIPTV: login host+usuario+senha, multi-perfil (varios portais salvos, como o XCIPTV), Live com EPG XMLTV, VOD, Series (temporadas/episodios), catch-up (`tv_archive`), `output=ts|m3u8`.

## Persistencia

Fase 2: perfis Xtream (multi-portal, como o XCIPTV), URLs de interface, favoritos e "continuar assistindo" (DataStore no Android, JSON no desktop, localStorage na TV).

## Legalidade

O projeto e um player generico. Nao distribui fontes de conteudo, spiders, jars de terceiros nem listas. Todo conteudo vem de configuracao escolhida pelo usuario (propria/aplicativos licenciados).
