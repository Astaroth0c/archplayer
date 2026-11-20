package br.archplayer.core.m3u

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
    val tvgShift: String? = null
)

data class M3uPlaylist(
    val guideUrl: String? = null,
    val entries: List<M3uEntry> = emptyList()
)

object M3uParser {

    private val guideRegex = Regex("x-tvg-url=\"([^\"]*)\"|url-tvg=\"([^\"]*)\"")
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): M3uPlaylist {
        var guideUrl: String? = null
        val entries = ArrayList<M3uEntry>()
        var pending: M3uEntry? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTM3U") -> {
                    val match = guideRegex.find(line)
                    if (match != null) {
                        val first = match.groupValues[1]
                        val second = match.groupValues[2]
                        guideUrl = when {
                            first.isNotEmpty() -> first
                            second.isNotEmpty() -> second
                            else -> null
                        }
                    }
                }

                line.startsWith("#EXTINF") -> {
                    val title = line.substringAfter(',', "").trim()
                    val meta = line.substringBefore(',', "")
                    val attrs = attrRegex.findAll(meta)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pending = M3uEntry(
                        name = title,
                        url = "",
                        tvgId = attrs["tvg-id"],
                        tvgName = attrs["tvg-name"],
                        tvgLogo = attrs["tvg-logo"],
                        groupTitle = attrs["group-title"],
                        tvgShift = attrs["tvg-shift"]
                    )
                }

                line.startsWith("#") -> Unit

                else -> {
                    val entry = pending
                    if (entry != null) {
                        entries.add(entry.copy(url = line, name = entry.name.ifBlank { urlName(line) }))
                        pending = null
                    } else {
                        entries.add(M3uEntry(name = urlName(line), url = line))
                    }
                }
            }
        }
        return M3uPlaylist(guideUrl = guideUrl, entries = entries)
    }

    private fun urlName(url: String): String =
        url.substringBefore("?").trimEnd('/').substringAfterLast('/').ifBlank { url }
}
