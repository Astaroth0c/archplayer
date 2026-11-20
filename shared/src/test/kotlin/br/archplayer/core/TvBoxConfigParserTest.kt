package br.archplayer.core

import br.archplayer.core.tvbox.TvBoxConfigParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvBoxConfigParserTest {

    private val sample = """
        {
          "spider": "./jar/pg.jar;md5;abc",
          "wallpaper": "https://example.com/wall.jpg",
          "flags": ["qq", "qiyi", "mgtv"],
          "headers": {"User-Agent": "okhttp/4.9.3"},
          "sites": [
            {"key": "site1", "name": "Site Um", "type": 1, "api": "https://a.example/api",
             "searchable": "1", "quickSearch": "1", "filterable": 1, "style": "{'type': 'rect', 'ratio': 1.33}"},
            {"key": "spider1", "name": "Csp", "type": 3, "api": "csp_YSGG", "ext": "https://x/ext.json",
             "searchable": "0"}
          ],
          "parses": [
            {"name": "Json1", "type": 0, "url": "https://p.example/?url=",
             "ext": {"flag": ["qq", "qiyi"]}},
            {"name": "Sniffer", "type": 3, "url": "Web"}
          ],
          "lives": [
            {"name": "GrupoA", "type": 1, "channels": [
              {"group": "G1", "channels": [
                {"name": "Canal 1", "urls": ["http://a/1.m3u8", "http://a/1b.m3u8"]}
              ]}
            ]},
            {"name": "Txt", "type": 0, "url": "https://live.example/list.txt", "ua": "okhttp/3.15"}
          ],
          "doh": [{"name": "Cloudflare", "url": "https://1.1.1.1/dns-query"}]
        }
    """.trimIndent()

    @Test
    fun parsesFullConfig() {
        val config = TvBoxConfigParser().parse(sample)
        assertEquals("./jar/pg.jar;md5;abc", config.spider)
        assertEquals(2, config.sites.size)
        assertEquals(2, config.parses.size)
        assertEquals(2, config.lives.size)
        assertEquals(listOf("qq", "qiyi", "mgtv"), config.flags)
        assertEquals("okhttp/4.9.3", config.headers["User-Agent"])
        assertEquals(1, config.doh.size)
    }

    @Test
    fun parsesLenientSiteFields() {
        val config = TvBoxConfigParser().parse(sample)
        val first = config.sites[0]
        assertEquals("Site Um", first.name)
        assertEquals(1, first.type)
        assertTrue(first.searchable)
        assertTrue(first.quickSearch)
        assertTrue(first.filterable)
        assertEquals("rect", first.style?.type)
        assertEquals(1.33, first.style?.ratio)
        val second = config.sites[1]
        assertTrue(!second.searchable)
        assertEquals("csp_YSGG", second.api)
    }

    @Test
    fun parsesInlineLiveGroups() {
        val config = TvBoxConfigParser().parse(sample)
        val grouped = config.lives[0]
        assertEquals(1, grouped.type)
        assertEquals(1, grouped.groups.size)
        assertEquals("Canal 1", grouped.groups[0].channels[0].name)
        assertEquals(2, grouped.groups[0].channels[0].urls.size)
        val txt = config.lives[1]
        assertEquals(0, txt.type)
        assertEquals("https://live.example/list.txt", txt.url)
    }
}
