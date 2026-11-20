package br.archplayer.core.live

class LiveTxtParser {

    fun parse(text: String): List<LiveTxtGroup> {
        val groups = ArrayList<LiveTxtGroup>()
        var current: LiveTxtGroup? = null
        var pendingName: String? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.endsWith(",#genre#") || line.endsWith("#genre#") && line.contains(",")) {
                val name = line.substringBeforeLast(",").removeSuffix("#genre#").trim()
                current = LiveTxtGroup(name = name.ifBlank { "Geral" })
                groups.add(current)
                pendingName = null
                continue
            }

            val comma = line.indexOf(',')
            if (comma > 0) {
                val name = line.substring(0, comma).trim()
                val url = line.substring(comma + 1).trim()
                if (name.isEmpty() || url.isEmpty()) continue
                current?.let { group ->
                    group.channels.add(LiveTxtChannel(name = name, urls = rebuildUrls(url)))
                } ?: run {
                    if (groups.isEmpty() || pendingName != null) {
                        val fallback = groups.lastOrNull() ?: LiveTxtGroup("Geral").also { groups.add(it) }
                        fallback.channels.add(LiveTxtChannel(name = name, urls = rebuildUrls(url)))
                    }
                    pendingName = null
                }
            }
        }
        return groups
    }

    private fun rebuildUrls(url: String): List<String> =
        url.split("#").filter { it.isNotBlank() }
}

class LiveTxtGroup(val name: String) {
    val channels = ArrayList<LiveTxtChannel>()
    val channelList: List<LiveTxtChannel> get() = channels
}

data class LiveTxtChannel(val name: String, val urls: List<String>)
