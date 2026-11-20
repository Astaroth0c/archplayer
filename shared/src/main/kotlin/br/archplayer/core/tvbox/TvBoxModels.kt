package br.archplayer.core.tvbox

import kotlinx.serialization.json.JsonElement

data class TvBoxConfig(
    val spider: String? = null,
    val sites: List<Site> = emptyList(),
    val parses: List<Parse> = emptyList(),
    val lives: List<Live> = emptyList(),
    val flags: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val wallpaper: String? = null,
    val doh: List<DohProvider> = emptyList()
)

data class Site(
    val key: String,
    val name: String,
    val type: Int,
    val api: String,
    val ext: JsonElement? = null,
    val searchable: Boolean = false,
    val quickSearch: Boolean = false,
    val filterable: Boolean = false,
    val changeable: Boolean = false,
    val header: Map<String, String> = emptyMap(),
    val style: SiteStyle? = null
)

data class SiteStyle(
    val type: String = "rec",
    val ratio: Double = 1.33
)

data class Parse(
    val name: String,
    val type: Int,
    val url: String? = null,
    val ext: JsonElement? = null,
    val flags: List<String> = emptyList(),
    val header: Map<String, String> = emptyMap()
)

data class Live(
    val name: String,
    val type: Int,
    val url: String,
    val ua: String? = null,
    val epg: String? = null,
    val header: Map<String, String> = emptyMap(),
    val groups: List<LiveGroup> = emptyList()
)

data class LiveGroup(
    val group: String,
    val channels: List<LiveChannel>
)

data class LiveChannel(
    val name: String,
    val urls: List<String>
)

data class DohProvider(
    val name: String,
    val url: String
)
