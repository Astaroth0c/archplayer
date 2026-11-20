package br.archplayer.core.live

import br.archplayer.core.xmltv.XmltvGuide
import br.archplayer.core.xmltv.XmltvProgramme

data class EpgSlot(
    val title: String,
    val start: Long,
    val stop: Long
)

data class EpgNowNext(
    val now: EpgSlot? = null,
    val next: EpgSlot? = null
)

object EpgLookup {

    fun byChannelId(guide: XmltvGuide?): Map<String, List<XmltvProgramme>> {
        if (guide?.programmes == null) return emptyMap()
        return guide.programmes.groupBy { it.channelId }
    }

    fun nowNext(programmes: List<XmltvProgramme>, currentTimeSec: Long): EpgNowNext {
        if (programmes.isEmpty()) return EpgNowNext()
        val sorted = programmes.sortedBy { it.start }
        val nowSlot = sorted.lastOrNull { it.start <= currentTimeSec && currentTimeSec < it.stop }
            ?: return EpgNowNext()
        val nextSlot = sorted.firstOrNull { it.start > nowSlot.start }
        return EpgNowNext(
            now = EpgSlot(nowSlot.title, nowSlot.start, nowSlot.stop),
            next = nextSlot?.let { EpgSlot(it.title, it.start, it.stop) }
        )
    }

    fun findProgrammes(
        epgIndex: Map<String, List<XmltvProgramme>>,
        tvgId: String?,
        channelName: String
    ): List<XmltvProgramme> {
        if (epgIndex.isEmpty()) return emptyList()
        tvgId?.let { id ->
            epgIndex[id]?.let { return it }
        }
        val normalized = normalize(channelName)
        epgIndex.entries.firstOrNull { (id, _) ->
            normalize(id) == normalized
        }?.let { return it.value }
        return emptyList()
    }

    fun normalize(name: String): String =
        name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
}
