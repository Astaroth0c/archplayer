package br.archplayer.core.xciptv

import kotlinx.serialization.SerialName
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
    val password: String? = null,
    val auth: Int? = null,
    val status: String? = null,
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("is_trial") val isTrial: String? = null,
    @SerialName("active_cons") val activeConnections: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: List<String> = emptyList()
)

@Serializable
data class XtreamServerInfo(
    val xui: Boolean? = null,
    val version: String? = null,
    val revision: Int? = null,
    val url: String? = null,
    val port: String? = null,
    @SerialName("https_port") val httpsPort: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null,
    @SerialName("rtmp_port") val rtmpPort: String? = null,
    @SerialName("timestamp_now") val timestampNow: Long? = null,
    @SerialName("time_now") val timeNow: String? = null,
    val timezone: String? = null
)

@Serializable
data class XtreamHandshake(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfo? = null
)

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("parent_id") val parentId: Int? = null
)

@Serializable
data class XtreamLiveStream(
    val num: Int? = null,
    val name: String? = null,
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("stream_id") val streamId: Int? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    val added: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("tv_archive") val tvArchive: Int? = null,
    @SerialName("tv_archive_duration") val tvArchiveDuration: Int? = null,
    @SerialName("direct_source") val directSource: String? = null
)

@Serializable
data class XtreamVodStream(
    val num: Int? = null,
    val name: String? = null,
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("stream_id") val streamId: Int? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    val rating: Double? = null,
    @SerialName("rating_5based") val rating5Based: Double? = null,
    val added: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
    @SerialName("direct_source") val directSource: String? = null
)

@Serializable
data class XtreamSeries(
    val num: Int? = null,
    val name: String? = null,
    @SerialName("series_id") val seriesId: Int? = null,
    val cover: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    @SerialName("releaseDate") val releaseDateRaw: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    val rating: String? = null,
    @SerialName("category_id") val categoryId: String? = null
)

@Serializable
data class XtreamEpisode(
    val id: String? = null,
    @SerialName("episode_num") val episodeNum: String? = null,
    val title: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
    @SerialName("info") val info: XtreamEpisodeInfo? = null,
    val added: String? = null,
    val season: Int? = null
)

@Serializable
data class XtreamEpisodeInfo(
    @SerialName("movie_image") val movieImage: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val duration: String? = null,
    @SerialName("duration_secs") val durationSecs: String? = null,
    @SerialName("cover_big") val coverBig: String? = null
)

@Serializable
data class XtreamSeason(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    val id: Int? = null,
    val name: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    val cover: String? = null
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
    @SerialName("cast") val castRaw: String? = null,
    val director: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val rating: String? = null,
    @SerialName("backdrop_path") val backdropPath: List<String>? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String? = null
)

@Serializable
data class XtreamVodInfo(
    val info: XtreamVodMeta? = null,
    @SerialName("movie_data") val movieData: XtreamVodData? = null
)

@Serializable
data class XtreamVodMeta(
    @SerialName("movie_image") val movieImage: String? = null,
    @SerialName("duration_secs") val durationSecs: String? = null,
    val duration: String? = null,
    val plot: String? = null,
    @SerialName("cast") val castRaw: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val rating: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String? = null
)

@Serializable
data class XtreamVodData(
    @SerialName("stream_id") val streamId: Int? = null,
    val name: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null
)
