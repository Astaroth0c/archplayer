package br.archplayer.core.xciptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
private data class HandshakeDto(
    val user_info: XtreamUserInfo? = null,
    val server_info: XtreamServerInfo? = null
)

@Serializable
private data class CategoriesDto(
    val category_id: String? = null,
    val category_name: String? = null,
    val parent_id: Int? = null
)

class XtreamClient(
    private val credentials: XtreamCredentials
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun baseUrl(): String {
        val host = credentials.host.trim().trimEnd('/')
        return if (host.startsWith("http")) host else "http://$host"
    }

    suspend fun handshake(): XtreamHandshake =
        requestJson(nullable = false) { XtreamHandshake(it.user_info, it.server_info) }

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
        categoryList { liveCategoriesRequest() }

    suspend fun vodCategories(): List<XtreamCategory> =
        categoryList { vodCategoriesRequest() }

    suspend fun seriesCategories(): List<XtreamCategory> =
        categoryList { seriesCategoriesRequest() }

    suspend fun liveStreams(categoryId: String? = null): List<XtreamLiveStream> =
        withContext(Dispatchers.IO) {
            val text = fetch("get_live_streams", "category_id", categoryId)
            json.decodeFromString<List<XtreamLiveStream>>(jsonArrayOrEmpty(text))
        }

    suspend fun vodStreams(categoryId: String? = null): List<XtreamVodStream> =
        withContext(Dispatchers.IO) {
            val text = fetch("get_vod_streams", "category_id", categoryId)
            json.decodeFromString<List<XtreamVodStream>>(jsonArrayOrEmpty(text))
        }

    suspend fun series(categoryId: String? = null): List<XtreamSeries> =
        withContext(Dispatchers.IO) {
            val text = fetch("get_series", "category_id", categoryId)
            json.decodeFromString<List<XtreamSeries>>(jsonArrayOrEmpty(text))
        }

    suspend fun seriesInfo(seriesId: Int): XtreamSeriesInfo =
        withContext(Dispatchers.IO) {
            val text = fetch("get_series_info", "series_id", seriesId.toString())
            json.decodeFromString<XtreamSeriesInfo>(jsonObjectOrEmpty(text))
        }

    suspend fun vodInfo(vodId: Int): XtreamVodInfo =
        withContext(Dispatchers.IO) {
            val text = fetch("get_vod_info", "vod_id", vodId.toString())
            json.decodeFromString<XtreamVodInfo>(jsonObjectOrEmpty(text))
        }

    private suspend fun liveCategoriesRequest(): String = fetch("get_live_categories", null, null)
    private suspend fun vodCategoriesRequest(): String = fetch("get_vod_categories", null, null)
    private suspend fun seriesCategoriesRequest(): String = fetch("get_series_categories", null, null)

    private suspend fun categoryList(fetcher: suspend () -> String): List<XtreamCategory> =
        withContext(Dispatchers.IO) {
            val text = fetcher()
            json.decodeFromString<List<CategoriesDto>>(jsonArrayOrEmpty(text)).mapNotNull { dto ->
                val id = dto.category_id ?: return@mapNotNull null
                XtreamCategory(categoryId = id, categoryName = dto.category_name ?: id, parentId = dto.parent_id)
            }
        }

    private suspend fun <T> requestJson(nullable: Boolean, map: (HandshakeDto) -> T): T {
        val text = withContext(Dispatchers.IO) { fetch("player_api.php", null, null) }
        val dto = runCatching { json.decodeFromString<HandshakeDto>(text.trim()) }
            .getOrElse { throw IllegalStateException("Resposta invalida do portal (${it.message ?: "erro"})") }
        return map(dto)
    }

    private fun fetch(action: String, extraKey: String?, extraValue: String?): String {
        val url = StringBuilder(baseUrl())
            .append("/player_api.php?")
            .append("username=").append(urlEncode(credentials.username))
            .append("&password=").append(urlEncode(credentials.password))
        if (action != "player_api.php") url.append("&action=").append(urlEncode(action))
        if (extraKey != null && !extraValue.isNullOrBlank()) {
            url.append("&").append(urlEncode(extraKey)).append("=").append(urlEncode(extraValue))
        }
        val connection = URL(url.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = connection.responseCode
            if (code < 200 || code >= 300) {
                throw IllegalStateException("Portal respondeu HTTP $code")
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Falha de conexao com o portal: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val USER_AGENT = "ArchPlayer/1.0"

        fun jsonArrayOrEmpty(text: String): String {
            val t = text.trim()
            return if (t.startsWith("[")) t else "[]"
        }

        fun jsonObjectOrEmpty(text: String): String {
            val t = text.trim()
            return if (t.startsWith("{")) t else "{}"
        }
    }
}

enum class XtreamStreamKind(val path: String) {
    LIVE("live"),
    VOD("movie"),
    SERIES("series")
}
