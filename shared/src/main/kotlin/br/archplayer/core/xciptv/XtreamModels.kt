package br.archplayer.core.xciptv

import kotlinx.serialization.Serializable

@Serializable
data class XtreamCredentials(
    val host: String,
    val username: String,
    val password: String
)

@Serializable
data class XtreamUserInfo(
    val username: String? = null,
    val status: String? = null,
    val auth: Int? = null,
    val expDate: String? = null,
    val isTrial: String? = null,
    val activeConnections: String? = null,
    val maxConnections: String? = null,
    val allowedOutputFormats: List<String> = emptyList()
)

@Serializable
data class XtreamServerInfo(
    val url: String? = null,
    val port: String? = null,
    val httpsPort: String? = null,
    val serverProtocol: String? = null,
    val rtmpPort: String? = null,
    val timezone: String? = null,
    val timestampNow: Long? = null,
    val timeNow: String? = null
)

@Serializable
data class XtreamHandshake(
    val userInfo: XtreamUserInfo? = null,
    val serverInfo: XtreamServerInfo? = null
)

@Serializable
data class XtreamCategory(
    val categoryId: String? = null,
    val categoryName: String? = null,
    val parentId: Int? = null
)

@Serializable
data class XtreamLiveStream(
    val num: Int? = null,
    val name: String? = null,
    val streamType: String? = null,
    val streamId: Int? = null,
    val streamIcon: String? = null,
    val epgChannelId: String? = null,
    val added: String? = null,
    val categoryId: String? = null,
    val tvArchive: Int? = null,
    val tvArchiveDuration: Int? = null,
    val directSource: String? = null
)

@Serializable
data class XtreamVodStream(
    val num: Int? = null,
    val name: String? = null,
    val streamType: String? = null,
    val streamId: Int? = null,
    val streamIcon: String? = null,
    val rating: Double? = null,
    val rating5Based: Double? = null,
    val added: String? = null,
    val categoryId: String? = null,
    val containerExtension: String? = null,
    val directSource: String? = null
)

@Serializable
data class XtreamSeries(
    val num: Int? = null,
    val name: String? = null,
    val seriesId: Int? = null,
    val cover: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val releaseDate: String? = null,
    val lastModified: String? = null,
    val rating: String? = null,
    val categoryId: String? = null
)

@Serializable
data class XtreamEpisode(
    val id: String? = null,
    val episodeNum: String? = null,
    val title: String? = null,
    val containerExtension: String? = null,
    val info: XtreamEpisodeInfo? = null,
    val added: String? = null,
    val season: Int? = null
)

@Serializable
data class XtreamEpisodeInfo(
    val movieImage: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val duration: String? = null,
    val durationSecs: String? = null,
    val coverBig: String? = null
)

@Serializable
data class XtreamSeriesInfo(
    val info: XtreamSeriesMeta? = null,
    val seasons: List<XtreamSeason> = emptyList(),
    val episodes: Map<String, List<XtreamEpisode>> = emptyMap()
)

@Serializable
data class XtreamSeriesMeta(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val backdropPath: List<String>? = null,
    val youtubeTrailer: String? = null
)

@Serializable
data class XtreamSeason(
    val airDate: String? = null,
    val episodeCount: Int? = null,
    val id: Int? = null,
    val name: String? = null,
    val seasonNumber: Int? = null,
    val cover: String? = null
)

@Serializable
data class XtreamVodInfo(
    val info: XtreamVodMeta? = null,
    val movieData: XtreamVodData? = null
)

@Serializable
data class XtreamVodMeta(
    val movieImage: String? = null,
    val durationSecs: String? = null,
    val duration: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val coverBig: String? = null,
    val youtubeTrailer: String? = null
)

@Serializable
data class XtreamVodData(
    val streamId: Int? = null,
    val name: String? = null,
    val containerExtension: String? = null
)
