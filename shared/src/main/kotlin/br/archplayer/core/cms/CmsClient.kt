package br.archplayer.core.cms

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    baseUrl: String,
    private val http: HttpClient = HttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }
) {
    private val api: String = baseUrl.trim().trimEnd('/')

    private suspend fun call(path: String): HttpResponse =
        http.get(api + path) {
            header("User-Agent", UA)
        }

    private suspend inline fun <reified T> get(path: String): T =
        json.decodeFromString(call(path).bodyAsText())

    suspend fun categories(): List<CmsCategory> =
        get<CmsClassResponse>("?ac=class").`class`.mapNotNull {
            val id = it.type_id ?: return@mapNotNull null
            CmsCategory(typeId = id, typeName = it.type_name ?: id)
        }

    suspend fun videos(categoryId: String?, page: Int = 1): Pair<List<CmsVideo>, Int> {
        val query = buildString {
            append("?ac=videolist")
            if (!categoryId.isNullOrBlank()) append("&t=").append(categoryId)
            append("&pg=").append(page.coerceAtLeast(1))
        }
        val res = get<CmsListResponse>(query)
        return res.list to (res.pagecount ?: 1)
    }

    suspend fun search(query: String, page: Int = 1): Pair<List<CmsVideo>, Int> {
        val res = get<CmsListResponse>("?ac=videolist&wd=" + URLEncoder.encode(query, "UTF-8") + "&pg=" + page.coerceAtLeast(1))
        return res.list to (res.pagecount ?: 1)
    }

    suspend fun detail(vodId: Int): CmsDetail? =
        get<CmsDetailResponse>("?ac=videolist&ids=" + vodId).list.firstOrNull()

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"
    }
}
