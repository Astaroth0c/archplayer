# Roadmap

## Fase 1 — Fundacao (concluida nesta entrega)
- [x] Monorepo Gradle (shared + androidApp + desktop) e app web TV (tizen/webos)
- [x] Parser de interfaces TVBox (aa.json/box.json/guanfang.json como corpus real)
- [x] Cliente Xtream Codes (logica XCIPTV) + parsers M3U/M3U+ e XMLTV
- [x] APK Android minSdk 24 (Android 7/Rockchip) com launcher TV + celular, Media3 HLS/DASH
- [x] Compose Desktop para Windows/Linux (.deb/.rpm/.exe/.msi)
- [x] Web app Tizen (.wgt) / webOS (.ipk) com hls.js

## Fase 2 — Player e conteudo
- [ ] Site type 1 (CMS JSON) e type 0 (Xipe) navegaveis (home/classificar/detalhe/episodios)
- [ ] Interface `PlayerEngine` + backend LibVLC (desktop e Android p/ streams dificeis)
- [ ] Multi-perfil Xtream com persistncia (estilo XCIPTV: varios portais salvos)
- [ ] EPG XMLTV integrado a grade ao vivo (agora/seguinte, timeline)
- [ ] Catch-up (tvArchive) e "continuar assistindo"
- [ ] Favoritos e busca global (cross-fontes)

## Fase 3 — Ecossistema TVBox avancado
- [ ] Spiders JS drpy (QuickJS embutido) para sites type 3
- [ ] Spiders jar (DexClassLoader) com sandbox e md5 check
- [ ] Parses (substituicao de URLs por API de parse) e sniffer WebView
- [ ] Alist/WebDAV/nuvem (libs lib/*.js das colecoes de referencia)

## Fase 4 — TVs Samsung/LG
- [ ] Certificacao Tizen Studio e instalacao via `sdb` (desenvolvimento)
- [ ] webOS TV via `ares-install` (modo dev)
- [ ] Publicacao nas lojas (Samsung Seller Portal / LG Content Store)

## Fase 5 — Polimento
- [ ] Controle remoto completo (D-pad, back, teclado numerico de canal)
- [ ] Player: bitrate/track selection, subs externas, ratio, escala
- [ ] Android TV: tela de detalhes estilo Leanback, rows com preview
- [ ] OTA de configs com cache e renovacao automatica
