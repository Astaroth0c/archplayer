package br.archplayer.core.xmltv

data class XmltvChannel(
    val id: String,
    val displayName: String,
    val icon: String? = null
)

data class XmltvProgramme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null
)

object XmltvParser {

    private val channelRegex = Regex("<channel[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL)
    private val programmeRegex = Regex(
        "<programme\\s+start=\"([^\"]+)\"\\s+stop=\"([^\"]+)\"\\s+channel=\"([^\"]+)\"[^>]*>(.*?)</programme>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val displayNameRegex = Regex("<display-name[^>]*>(.*?)</display-name>", RegexOption.DOT_MATCHES_ALL)
    private val iconRegex = Regex("<icon[^>]*src=\"([^\"]+)\"")
    private val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val descRegex = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)
    private val categoryRegex = Regex("<category[^>]*>(.*?)</category>", RegexOption.DOT_MATCHES_ALL)
    private val entityRegex = Regex("&(amp|lt|gt|quot|apos);")
    private val offsetRegex = Regex("([+-])(\\d{2})(\\d{2})$")

    fun parse(text: String): XmltvGuide {
        val channels = channelRegex.findAll(text).map { channelMatch ->
            val body = channelMatch.groupValues[2]
            XmltvChannel(
                id = channelMatch.groupValues[1],
                displayName = displayNameRegex.find(body)?.groupValues?.get(1)?.unlessEmpty()
                    ?: entityDecode(channelMatch.groupValues[1]),
                icon = iconRegex.find(body)?.groupValues?.get(1)
            )
        }.toList()

        val programmes = programmeRegex.findAll(text).map { progMatch ->
            val body = progMatch.groupValues[4]
            XmltvProgramme(
                channelId = progMatch.groupValues[3],
                start = parseXmltvTime(progMatch.groupValues[1]),
                stop = parseXmltvTime(progMatch.groupValues[2]),
                title = titleRegex.find(body)?.groupValues?.get(1)?.unlessEmpty() ?: "",
                description = descRegex.find(body)?.groupValues?.get(1)?.unlessEmpty(),
                category = categoryRegex.find(body)?.groupValues?.get(1)?.unlessEmpty()
            )
        }.toList()

        return XmltvGuide(channels = channels, programmes = programmes)
    }

    fun parseXmltvTime(value: String): Long {
        val clean = value.trim()
        val digits = clean.take(14)
        if (digits.length < 14) return 0L
        val year = digits.substring(0, 4).toIntOrNull() ?: return 0L
        val month = digits.substring(4, 6).toIntOrNull() ?: 1
        val day = digits.substring(6, 8).toIntOrNull() ?: 1
        val hour = digits.substring(8, 10).toIntOrNull() ?: 0
        val minute = digits.substring(10, 12).toIntOrNull() ?: 0
        val second = digits.substring(12, 14).toIntOrNull() ?: 0
        val epoch = daysFromEpoch(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second
        val tz = offsetRegex.find(clean) ?: return epoch
        val sign = tz.groupValues[1]
        val oh = (tz.groupValues[2].toIntOrNull() ?: 0)
        val om = (tz.groupValues[3].toIntOrNull() ?: 0)
        val offsetSeconds = oh * 3_600 + om * 60
        return if (sign == "-") epoch + offsetSeconds else epoch - offsetSeconds
    }

    private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
        var y = year.toLong()
        val m = month.toLong()
        if (m <= 2) y -= 1
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    private fun entityDecode(value: String): String = entityRegex.replace(value) { m ->
        when (m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> m.value
        }
    }

    private fun String.unlessEmpty(): String? = if (isBlank()) null else this
}

data class XmltvGuide(
    val channels: List<XmltvChannel> = emptyList(),
    val programmes: List<XmltvProgramme> = emptyList()
)
