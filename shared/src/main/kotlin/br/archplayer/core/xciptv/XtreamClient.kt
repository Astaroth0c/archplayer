package br.archplayer.core.xciptv

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
class XtreamClient(
    private val credentials: XtreamCredentials,
    private val http: HttpClient = HttpClient(),
    private val usernameTag: String = "username",
    private val passwordTag: String = "password"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun baseUrl(): String {
        val host = credentials.host.trim().trimEnd('/')
        return if (host.startsWith("http")) host else "http://$host"
    }

    suspend fun handshake(): XtreamHandshake =
        decode("player_api.php")

    fun streamUrl(kind: XtreamStreamKind, id: Int, container: String? = null): String {
        val ext = container?.removePrefix(".")?.let { ".$it" } ?: ""
        val c = credentials
        return "${baseUrl()}/${kind.path}/${c.username}/${c.password}/$id$ext"
    }

    fun m3uUrl(output: String = "ts"): String {
        val c = credentials
        return "${baseUrl()}/get.php?" + listOf(
            "username" to c.username,
            "password" to c.password,
            "type" to "m3u_plus",
            "output" to output
        ).joinToString("&") { "${it.first}=${it.second}" }
    }

    fun epgUrl(): String {
        val c = credentials
        return "${baseUrl()}/xmltv.php?" + listOf(
            "username" to c.username,
            "password" to c.password
        ).joinToString("&") { "${it.first}=${it.second}" }
    }

    suspend fun liveCategories(): List<XtreamCategory> =
        decode("get_live_categories")

    suspend fun vodCategories(): List<XtreamCategory> =
        decode("get_vod_categories")

    suspend fun seriesCategories(): List<XtreamCategory> =
        decode("get_series_categories")

    suspend fun liveStreams(categoryId: String? = null): List<XtreamLiveStream> =
        decodeList("get_live_streams", "category_id", categoryId)

    suspend fun vodStreams(categoryId: String? = null): List<XtreamVodStream> =
        decodeList("get_vod_streams", "category_id", categoryId)

    suspend fun series(categoryId: String? = null): List<XtreamSeries> =
        decodeList("get_series", "category_id", categoryId)

    suspend fun seriesInfo(seriesId: Int): XtreamSeriesInfo =
        decode("get_series_info", "series_id", seriesId.toString())

    suspend fun vodInfo(vodId: Int): XtreamVodInfo =
        decode("get_vod_info", "vod_id", vodId.toString())

    suspend fun rawText(action: String, categoryId: String? = null): String =
        fetch(action, "category_id", categoryId).bodyAsText()

    private suspend inline fun <reified T> decode(action: String, extraKey: String? = null, extraValue: String? = null): T =
        decodeBody(json, fetch(action, extraKey, extraValue).bodyAsText())

    private suspend inline fun <reified T> decodeList(action: String, extraKey: String?, extraValue: String?): List<T> =
        decodeBodyList(json, fetch(action, extraKey, extraValue).bodyAsText())

    private suspend fun fetch(action: String, extraKey: String?, extraValue: String?) : io.ktor.client.statement.HttpResponse {
        val builder = StringBuilder(baseUrl())
            .append("/player_api.php?")
            .append(usernameTag).append("=").append(urlEncode(credentials.username))
            .append("&").append(passwordTag).append("=").append(urlEncode(credentials.password))
        if (action != "player_api.php") builder.append("&action=").append(urlEncode(action))
        if (extraKey != null && !extraValue.isNullOrBlank()) {
            builder.append("&").append(urlEncode(extraKey)).append("=").append(urlEncode(extraValue))
        }
        return http.get(builder.toString()) {
            header("User-Agent", USER_AGENT)
        }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        const val USER_AGENT = "ArchPlayer/1.0"
    }
}

internal inline fun <reified T> decodeBody(json: Json, text: String): T =
    json.decodeFromString(text.trim())

internal inline fun <reified T> decodeBodyList(json: Json, text: String): List<T> {
    val trimmed = text.trim()
    val body = if (trimmed.startsWith("[") || trimmed.startsWith("{")) trimmed
        else "[]"
    return try {
        json.decodeFromString(body)
    } catch (e: kotlinx.serialization.SerializationException) {
        json.decodeFromString("[]")
    }
}

enum class XtreamStreamKind(val path: String) {
    LIVE("live"),
    VOD("movie"),
    SERIES("series")
}
