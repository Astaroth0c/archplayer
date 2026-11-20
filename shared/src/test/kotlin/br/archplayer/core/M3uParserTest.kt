package br.archplayer.core

import br.archplayer.core.m3u.M3uParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class M3uParserTest {

    private val sample = """
        #EXTM3U x-tvg-url="https://epg.example/guide.xml"
        #EXTINF:-1 tvg-id="glor.pt" tvg-name="Gloria" tvg-logo="https://l.example/g.png" group-title="Noticias",Gloria Noticias
        http://stream.example/gloria.m3u8
        #EXTINF:-1 group-title="Desporto",Sport TV HD
        http://stream.example/sport.m3u8
        http://stream.example/fallback.mp4
    """.trimIndent()

    @Test
    fun parsesHeaderGuideAndEntries() {
        val playlist = M3uParser.parse(sample)
        assertEquals("https://epg.example/guide.xml", playlist.guideUrl)
        assertEquals(3, playlist.entries.size)

        val first = playlist.entries[0]
        assertEquals("Gloria Noticias", first.name)
        assertEquals("glor.pt", first.tvgId)
        assertEquals("Gloria", first.tvgName)
        assertEquals("https://l.example/g.png", first.tvgLogo)
        assertEquals("Noticias", first.groupTitle)

        val second = playlist.entries[1]
        assertEquals("Desporto", second.groupTitle)
        assertNull(second.tvgId)

        assertEquals("http://stream.example/fallback.mp4", playlist.entries[2].url)
        assertNotNull(playlist.entries[2].name)
    }

    @Test
    fun toleratesEmptyInput() {
        val playlist = M3uParser.parse("")
        assertEquals(0, playlist.entries.size)
        assertNull(playlist.guideUrl)
    }
}
