# Análise dos repositórios de referência

## 1. cluntop/tvbox

**O que é:** distribuição de *interfaces* (configs) do ecossistema TVBox — não contém código-fonte de app. Servido como site estático (Cloudflare pages, ver `_headers`/`_redirects`) e repositório de conteúdo.

Artefatos relevantes:

| Artefato | Papel |
|---|---|
| `aa.json`, `box.json`, `fun.json`, `jsm.json`, `wv.json`, `test.json` | Interfaces completas (config TVBox) |
| `pg.jar`, `jar/` | "Spiders" compilados (dex/jar) carregados dinamicamente pelo app — sites `type: 3` com `api: csp_*` |
| `js/*.js` | Spiders drpy/GMSpider em JavaScript (ex.: `drpymin2.js`, `drpy2.min.js`, `cheerio.min.js` como runtime) |
| `py/*.py` | Spiders Python (rodam via proxy névoa/py_api do app original) |
| `lib/*.m3u`, `lib/iptv.txt`, `lib/alist.min.js`, `lib/douban.json` | Listas de lives, integrações (Alist, douban hot), utilidades |
| `.github/workflows` | Automação de publicação |

Formato da interface (extraído de `aa.json`):
- `spider`: jar/dex global (com sufixo opcional `;md5;<hash>`)
- `sites[]`: `key, name, type, api, ext, searchable, quickSearch, filterable, changeable, style`
  - `type 0`: API MAC-style (voda/XPath style), `type 1`: JSON API (cms), `type 3`: spider custom (csp_* jar ou JS drpy)
  - `style` aparece **como string** `{'type': 'rect', 'ratio': 1.33}` — precisa de parser tolerante
  - flags booleanas aparecem como `"1"`/`"0"`/número — idem
- `parses[]`: `type 0` = JSON union local/online, `type 1` = JSON union, `type 2` = JSON扩展, `type 3` = sniffer/WebView
- `lives[]`: `type 0` = txt (m3u-like remoto), `type 1` = grupos inline `channels[].channels[].{name,urls[]}`
- `flags[]`: famílias de player (qq, qiyi, mgtv...) usadas para casar parse ↔ fonte
- `doh[]`, `headers`, `wallpaper`, `rules`, `ads`, `ijk` (opções de player)

**Ética/legal:** grande parte dos spiders aponta para fontes não licenciadas (e há pasta com conteúdo adulto). O ArchPlayer deve tratar configs como *formato de dados* e deixar o usuário escolher/carregar as próprias fontes (mesmo modelo do Kodi/TVMob). Nada de distribuir fontes ou jar de terceiros no app.

## 2. shizi8402/TVbox-interface

**O que é:** coleção histórica de interfaces e binários de spiders (2018–2022): `*.json` (BiuIndex, guanfang, mao*, kan14...), `*.jar`/`classes.dex` spiders, listas `lives` inline, docs (BiuBiuTV.md). Útil como *corpus de compatibilidade*: mostra variações do formato (chaves chinesas nativas em `BiuIndex.json`, `guanfang.json` com chaves `sites/lives/parses/flags/ijk/ads/spider`, etc.).

Relevância para o ArchPlayer: casos de teste de parse e compatibilidade retroativa; nenhum código de app reaproveitável.

## Conclusão

Nenhum dos dois repositórios tem código de aplicativo — são o "lado da configuração". Para o app em si a referência técnica é a família TVBoxOSC (código aberto) e o comportamento praticado pelos players comerciais tipo XCIPTV (Xtream Codes API + XMLTV + M3U+). O ArchPlayer consome os recursos destes repositórios *como dados* (interfaces), e este projeto implementa o lado do player do zero.
