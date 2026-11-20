package br.archplayer.core.cms

import kotlinx.serialization.Serializable

@Serializable
data class CmsCategory(
    val typeId: String,
    val typeName: String
)

@Serializable
data class CmsVideo(
    val vodId: Int,
    val vodName: String,
    val vodPic: String? = null,
    val vodRemarks: String? = null,
    val vodTime: String? = null
)

@Serializable
data class CmsDetail(
    val vodId: Int,
    val vodName: String,
    val vodPic: String? = null,
    val vodClass: String? = null,
    val vodArea: String? = null,
    val vodYear: String? = null,
    val vodActor: String? = null,
    val vodDirector: String? = null,
    val vodContent: String? = null,
    val vodRemarks: String? = null,
    val vodPlayUrl: String? = null
)

data class PlayPlaylist(val name: String, val episodes: List<PlayEpisode>)
data class PlayEpisode(val title: String, val url: String)

fun CmsDetail.playlists(): List<PlayPlaylist> {
    val raw = vodPlayUrl ?: return emptyList()
    return raw.split("$$$").map { playlist ->
        val name = playlist.substringBefore("\$", "").ifBlank { "Padrao" }
        val body = playlist.substringAfter("\$", playlist)
        val items = body.split("#")
            .mapNotNull { chunk ->
                val url = chunk.substringAfter("\$", "")
                val title = chunk.substringBefore("\$", "").ifBlank { null }
                if (url.isBlank()) null else PlayEpisode(title ?: "Item", url)
            }
        PlayPlaylist(name, items)
    }
}
