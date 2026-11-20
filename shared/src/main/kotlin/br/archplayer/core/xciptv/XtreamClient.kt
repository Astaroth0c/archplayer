package br.archplayer.core.xciptv

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode

class XtreamClient(
    private val credentials: XtreamCredentials,
    private val http: HttpClient = HttpClient(),
    private val usernameTag: String = "username",
    private val passwordTag: String = "password"
) {

    private fun baseUrl(): String {
        val host = credentials.host.trim().trimEnd('/')
        return if (host.startsWith("http")) host else "http://" + host
    }

    suspend fun handshake(): XtreamHandshake =
        get("player_api.php").body()

    fun streamUrl(kind: XtreamStreamKind, id: Int, container: String? = null): String {
        val ext = container?.removePrefix(".")?.let { ".$it" } ?: ""
        val c = credentials
        return "${baseUrl()}/${kind.path}/${c.username}/${c.password}/${id}$ext"
    }

    fun m3uUrl(output: String = "ts"): String {
        val c = credentials
        return "${baseUrl()}/get.php?${listOf(
            "username" to c.username,
            "password" to c.password,
            "type" to "m3u_plus",
            "output" to output
        ).joinToString("&") { "${it.first}=${it.second}" }}"
    }

    fun epgUrl(): String {
        val c = credentials
        return "${baseUrl()}/xmltv.php?${listOf(
            "username" to c.username,
            "password" to c.password
        ).joinToString("&") { "${it.first}=${it.second}" }}"
    }

    suspend fun liveCategories(): List<XtreamCategory> =
        get("get_live_categories").body()

    suspend fun vodCategories(): List<XtreamCategory> =
        get("get_vod_categories").body()

    suspend fun seriesCategories(): List<XtreamCategory> =
        get("get_series_categories").body()

    suspend fun liveStreams(categoryId: String? = null): List<XtreamLiveStream> =
        get("get_live_streams", "category_id", categoryId).body()

    suspend fun vodStreams(categoryId: String? = null): List<XtreamVodStream> =
        get("get_vod_streams", "category_id", categoryId).body()

    suspend fun series(categoryId: String? = null): List<XtreamSeries> =
        get("get_series", "category_id", categoryId).body()

    suspend fun seriesInfo(seriesId: Int): XtreamSeriesInfo =
        get("get_series_info", "series_id", seriesId.toString()).body()

    suspend fun vodInfo(vodId: Int): XtreamVodInfo =
        get("get_vod_info", "vod_id", vodId.toString()).body()

    suspend fun rawText(action: String, categoryId: String? = null): String =
        fetch(action, "category_id", categoryId).bodyAsText()

    private suspend fun get(action: String, extraKey: String? = null, extraValue: String? = null): HttpResponse =
        fetch(action, extraKey, extraValue)

    private suspend fun fetch(action: String, extraKey: String?, extraValue: String?): HttpResponse {
        val params = ParametersBuilder()
        params.append(usernameTag, credentials.username)
        params.append(passwordTag, credentials.password)
        if (action != "player_api.php") params.append("action", action)
        if (extraKey != null && !extraValue.isNullOrBlank()) params.append(extraKey, extraValue)
        return http.get(baseUrl() + "/player_api.php?" + params.build().formUrlEncode()) {
            header("User-Agent", USER_AGENT)
        }
    }

    companion object {
        const val USER_AGENT = "ArchPlayer/1.0"
    }
}

enum class XtreamStreamKind(val path: String) {
    LIVE("live"),
    VOD("movie"),
    SERIES("series")
}
