package br.archplayer.core.tvbox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class TvBoxConfigParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {

    fun parse(text: String): TvBoxConfig {
        val root = json.parseToJsonElement(text).jsonObject
        return parseRoot(root)
    }

    fun parseRoot(root: JsonObject): TvBoxConfig = TvBoxConfig(
        spider = root.stringField("spider"),
        sites = root.array("sites").mapNotNull { (it as? JsonObject)?.let(::parseSite) },
        parses = root.array("parses").mapNotNull { (it as? JsonObject)?.let(::parseParse) },
        lives = root.array("lives").mapNotNull { (it as? JsonObject)?.let(::parseLive) },
        flags = root.array("flags").mapNotNull { (it as? JsonPrimitive)?.content },
        headers = root.objectMap("headers"),
        wallpaper = root.stringField("wallpaper"),
        doh = root.array("doh").mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val name = o.stringField("name") ?: return@mapNotNull null
            val url = o.stringField("url") ?: return@mapNotNull null
            DohProvider(name = name, url = url)
        }
    )

    private fun parseSite(o: JsonObject): Site = Site(
        key = o.stringField("key") ?: o.stringField("name") ?: "",
        name = o.stringField("name") ?: o.stringField("key") ?: "",
        type = o.intField("type") ?: 0,
        api = o.stringField("api") ?: "",
        ext = o["ext"],
        searchable = o.truthy("searchable"),
        quickSearch = o.truthy("quickSearch"),
        filterable = o.truthy("filterable"),
        changeable = o.truthy("changeable"),
        header = o.objectMap("header"),
        style = o["style"]?.let { parseStyle(it) }
    )

    private fun parseStyle(el: JsonElement): SiteStyle {
        val value = when (el) {
            is JsonPrimitive -> el.content
            else -> el.toString()
        }
        val cleaned = value.trim().trim('"', '\'').trim()
        val body = if (cleaned.startsWith("{")) cleaned.trim('{', '}') else cleaned
        val type = typeRegex.find(body)?.groupValues?.get(1) ?: SiteStyle().type
        val ratio = ratioRegex.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: SiteStyle().ratio
        return SiteStyle(type = type, ratio = ratio)
    }

    private fun parseParse(o: JsonObject): Parse = Parse(
        name = o.stringField("name") ?: "",
        type = o.intField("type") ?: 0,
        url = o.stringField("url"),
        ext = o["ext"],
        flags = o.array("flags").mapNotNull { (it as? JsonPrimitive)?.content },
        header = o.objectMap("header")
    )

    private fun parseLive(o: JsonObject): Live = Live(
        name = o.stringField("name") ?: o.stringField("group") ?: "",
        type = o.intField("type") ?: if (o.containsKey("channels")) 1 else 0,
        url = o.stringField("url") ?: "",
        ua = o.stringField("ua"),
        epg = o.stringField("epg"),
        header = o.objectMap("header"),
        groups = o.array("channels").mapNotNull { (it as? JsonObject)?.let(::parseLiveGroup) }
    )

    private fun parseLiveGroup(o: JsonObject): LiveGroup? {
        val channels = o.array("channels").mapNotNull { (it as? JsonObject)?.let(::parseLiveChannel) }
        if (channels.isEmpty()) return null
        return LiveGroup(group = o.stringField("group") ?: "canais", channels = channels)
    }

    private fun parseLiveChannel(o: JsonObject): LiveChannel? {
        val name = o.stringField("name") ?: return null
        val urls = o.array("urls").mapNotNull { (it as? JsonPrimitive)?.content }
        if (name.isBlank() || urls.isEmpty()) return null
        return LiveChannel(name = name, urls = urls)
    }

    companion object {
        private val typeRegex = Regex("['\"]?type['\"]?\\s*:\\s*['\"]?(\\w+)['\"]?")
        private val ratioRegex = Regex("['\"]?ratio['\"]?\\s*:\\s*([0-9.]+)")
    }
}

internal fun JsonObject.stringField(key: String): String? {
    val el = this[key] ?: return null
    return when (el) {
        is JsonPrimitive -> el.content
        else -> null
    }
}

internal fun JsonObject.intField(key: String): Int? = stringField(key)?.trim()?.toIntOrNull()

internal fun JsonObject.truthy(key: String): Boolean {
    val el = this[key] ?: return false
    return when (el) {
        is JsonPrimitive -> {
            if (el.content == "true") true
            else el.content.trim().toIntOrNull() == 1
        }
        else -> false
    }
}

internal fun JsonObject.array(key: String): List<JsonElement> {
    val el = this[key] as? JsonArray ?: return emptyList()
    return el.toList()
}

internal fun JsonObject.objectMap(key: String): Map<String, String> {
    val el = this[key] as? JsonObject ?: return emptyMap()
    return el.entries.mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.let { k to it.content }
    }.toMap()
}
