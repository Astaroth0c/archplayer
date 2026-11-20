package br.archplayer.core.cms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class CmsClassResponse(
    val `class`: List<CmsClassItem> = emptyList()
)

@Serializable
data class CmsClassItem(
    val type_id: String? = null,
    val type_name: String? = null
)

@Serializable
data class CmsListResponse(
    val page: Int? = null,
    val pagecount: Int? = null,
    val total: Int? = null,
    val list: List<CmsVideo> = emptyList()
)

@Serializable
data class CmsDetailResponse(
    val list: List<CmsDetail> = emptyList()
)

class CmsClient(
    baseUrl: String
) {
    private val api: String = baseUrl.trim().trimEnd('/')

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun call(path: String): String {
        val connection = URL(api + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", UA)
        try {
            val code = connection.responseCode
            if (code < 200 || code >= 300) throw IllegalStateException("HTTP $code")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Falha de conexao: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private inline fun <reified T> get(path: String): T =
        json.decodeFromString(rawJsonOrEmpty(call(path)).trim())

    suspend fun categories(): List<CmsCategory> = withContext(Dispatchers.IO) {
        get<CmsClassResponse>("?ac=class").`class`.mapNotNull {
            val id = it.type_id ?: return@mapNotNull null
            CmsCategory(typeId = id, typeName = it.type_name ?: id)
        }
    }

    suspend fun videos(categoryId: String?, page: Int = 1): Pair<List<CmsVideo>, Int> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("?ac=videolist")
            if (!categoryId.isNullOrBlank()) append("&t=").append(URLEncoder.encode(categoryId, "UTF-8"))
            append("&pg=").append(page.coerceAtLeast(1))
        }
        val res = get<CmsListResponse>(query)
        res.list to (res.pagecount ?: 1)
    }

    suspend fun search(query: String, page: Int = 1): Pair<List<CmsVideo>, Int> = withContext(Dispatchers.IO) {
        val res = get<CmsListResponse>("?ac=videolist&wd=" + URLEncoder.encode(query, "UTF-8") + "&pg=" + page.coerceAtLeast(1))
        res.list to (res.pagecount ?: 1)
    }

    suspend fun detail(vodId: Int): CmsDetail? = withContext(Dispatchers.IO) {
        get<CmsDetailResponse>("?ac=videolist&ids=" + vodId).list.firstOrNull()
    }

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"

        fun rawJsonOrEmpty(text: String): String {
            val t = text.trim()
            return if (t.startsWith("{") || t.startsWith("[")) t else "{}"
        }
    }
}
