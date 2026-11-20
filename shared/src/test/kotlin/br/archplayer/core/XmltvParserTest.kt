package br.archplayer.core

import br.archplayer.core.xmltv.XmltvParser
import kotlin.test.Test
import kotlin.test.assertEquals

class XmltvParserTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv generator="epg">
          <channel id="glor.pt">
            <display-name>Gloria Noticias</display-name>
            <icon src="https://l.example/g.png"/>
          </channel>
          <programme start="20240101120000 +0000" stop="20240101130000 +0000" channel="glor.pt">
            <title>Manchetes</title>
            <desc>Resumo das noticias do dia</desc>
            <category>Noticias</category>
          </programme>
          <programme start="20240101090000 -0300" stop="20240101100000 -0300" channel="glor.pt">
            <title>Esportes</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun parsesChannelsAndProgrammes() {
        val guide = XmltvParser.parse(sample)
        assertEquals(1, guide.channels.size)
        assertEquals(2, guide.programmes.size)

        val channel = guide.channels[0]
        assertEquals("glor.pt", channel.id)
        assertEquals("Gloria Noticias", channel.displayName)
        assertEquals("https://l.example/g.png", channel.icon)

        val first = guide.programmes[0]
        assertEquals("glor.pt", first.channelId)
        assertEquals("Manchetes", first.title)
        assertEquals("Resumo das noticias do dia", first.description)
        assertEquals("Noticias", first.category)
    }

    @Test
    fun convertsXmltvTimesWithOffsets() {
        val utc = XmltvParser.parseXmltvTime("20240101120000 +0000")
        val minusThree = XmltvParser.parseXmltvTime("20240101090000 -0300")
        assertEquals(utc, minusThree)
    }
}
