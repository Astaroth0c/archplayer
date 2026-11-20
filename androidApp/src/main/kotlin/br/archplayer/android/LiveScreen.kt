package br.archplayer.android

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.archplayer.core.live.EpgLookup
import br.archplayer.core.live.EpgNowNext
import br.archplayer.core.live.LiveTxtParser
import br.archplayer.core.m3u.M3uEntry
import br.archplayer.core.m3u.M3uParser
import br.archplayer.core.tvbox.TvBoxConfig
import br.archplayer.core.xciptv.XtreamClient
import br.archplayer.core.xciptv.XtreamCredentials
import br.archplayer.core.xmltv.XmltvParser
import br.archplayer.core.xmltv.XmltvProgramme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class UiChannel(
    val name: String,
    val urls: List<String>,
    val tvgId: String? = null
)

private data class UiGroup(
    val name: String,
    val channels: List<UiChannel>
)

private fun m3uToGroups(entries: List<M3uEntry>): List<UiGroup> =
    entries.groupBy { it.groupTitle ?: "Geral" }.map { (group, list) ->
        UiGroup(group, list.map { UiChannel(it.name, listOf(it.url), it.tvgId) })
    }

private fun txtToGroups(text: String): List<UiGroup> =
    LiveTxtParser().parse(text).map { group ->
        UiGroup(group.name, group.channels.map { UiChannel(it.name, it.urls) })
    }

@Composable
fun LiveScreen(
    config: TvBoxConfig?,
    prefs: SharedPreferences,
    onPlay: (String, String?) -> Unit
) {
    var groups by remember { mutableStateOf(listOf<UiGroup>()) }
    var epgIndex by remember { mutableStateOf(emptyMap<String, List<XmltvProgramme>>()) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var configLoaded by remember { mutableStateOf(false) }
    var showXtreamForm by remember { mutableStateOf(false) }
    var xHost by remember { mutableStateOf(prefs.getString("xtream_host", "") ?: "") }
    var xUser by remember { mutableStateOf(prefs.getString("xtream_user", "") ?: "") }
    var xPass by remember { mutableStateOf(prefs.getString("xtream_pass", "") ?: "") }
    val scope = rememberCoroutineScope()

    suspend fun loadEpg(url: String) {
        runCatching {
            withContext(Dispatchers.IO) { fetchText(url) }
        }.onSuccess { xml ->
            epgIndex = EpgLookup.byChannelId(XmltvParser.parse(xml))
        }.onFailure {
            status = "EPG indisponivel: ${it.message}"
        }
    }

    fun connectXtream() {
        if (xHost.isBlank() || xUser.isBlank() || xPass.isBlank()) {
            status = "Preencha host, usuario e senha do portal."
            return
        }
        prefs.edit()
            .putString("xtream_host", xHost.trim())
            .putString("xtream_user", xUser.trim())
            .putString("xtream_pass", xPass.trim())
            .apply()
        loading = true
        status = "Conectando ao portal..."
        scope.launch {
            val epgUrl = withContext(Dispatchers.IO) {
                runCatching {
                    val client = XtreamClient(
                        XtreamCredentials(host = xHost.trim(), username = xUser.trim(), password = xPass.trim())
                    )
                    val info = client.handshake()
                    if (info.userInfo == null) throw IllegalStateException("resposta invalida do portal")
                    val streams = client.liveStreams()
                    val base = xHost.trim().trimEnd('/')
                    val chans = streams.map { s ->
                        UiChannel(
                            name = s.name ?: ("Canal ${s.streamId}"),
                            urls = s.streamId?.let { id -> listOf("$base/live/${xUser.trim()}/${xPass.trim()}/$id.m3u8") } ?: emptyList(),
                            tvgId = s.epgChannelId
                        )
                    }
                    withContext(Dispatchers.Main) {
                        groups = listOf(UiGroup("Portal Xtream", chans))
                        selectedGroup = "Portal Xtream"
                    }
                    streams.size
                }.getOrNull()
                runCatching {
                    val client = XtreamClient(
                        XtreamCredentials(host = xHost.trim(), username = xUser.trim(), password = xPass.trim())
                    )
                    client.epgUrl()
                }.getOrNull()
            }
            if (epgUrl != null) loadEpg(epgUrl)
            loading = false
        }
    }

    LaunchedEffect(config) {
        if (config == null || configLoaded) return@LaunchedEffect
        configLoaded = true
        if (config.lives.isEmpty()) return@LaunchedEffect
        loading = true
        withContext(Dispatchers.IO) {
            val result = ArrayList<UiGroup>()
            config.lives.forEach { live ->
                if (live.type == 1 && live.groups.isNotEmpty()) {
                    live.groups.forEach { g ->
                        result.add(UiGroup(g.group, g.channels.map { UiChannel(it.name, it.urls) }))
                    }
                } else if (live.url.isNotBlank() && live.url.startsWith("http")) {
                    runCatching { fetchText(live.url) }.onSuccess { text ->
                        val trimmed = text.trimStart()
                        val parsed = if (trimmed.startsWith("#EXTM3U")) {
                            m3uToGroups(M3uParser.parse(text).entries)
                        } else {
                            txtToGroups(text)
                        }
                        synchronized(result) { result.addAll(parsed) }
                        live.epg?.takeIf { it.isNotBlank() }?.let { epg ->
                        }
                    }
                }
            }
            result
        }.let { loaded ->
            if (loaded.isNotEmpty()) {
                groups = loaded
                selectedGroup = loaded.firstOrNull()?.name
                status = "${loaded.sumOf { g -> g.channels.size }} canais carregados"
            }
            loading = false
        }
        config.lives.firstOrNull { it.epg != null && it.epg!!.isNotBlank() }?.epg?.let { loadEpg(it) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showXtreamForm = !showXtreamForm }) {
                Text("Portal Xtream (XCIPTV)")
            }
            Spacer(Modifier.width(8.dp))
            if (loading) CircularProgressIndicator(Modifier.size(20.dp))
        }

        if (showXtreamForm) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = xHost, onValueChange = { xHost = it }, label = { Text("Host (http://servidor:porta)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = xUser, onValueChange = { xUser = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = xPass, onValueChange = { xPass = it }, label = { Text("Senha") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { connectXtream() }) { Text("Conectar") }
            }
        }

        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sem canais. Carregue uma interface com lives ou conecte um portal Xtream.")
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    groups.forEach { group ->
                        TextButton(onClick = { selectedGroup = group.name }, modifier = Modifier.fillMaxWidth()) {
                            Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val channels = groups.firstOrNull { it.name == selectedGroup }?.channels ?: emptyList()
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                LazyColumn(Modifier.weight(1f)) {
                    items(channels, key = { it.name + it.urls.firstOrNull().orEmpty() }) { channel ->
                        val nowNext = nowNextFor(epgIndex, channel)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val url = channel.urls.firstOrNull() ?: return@clickable
                                    onPlay(url, null)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(channel.name, style = MaterialTheme.typography.bodyLarge)
                            nowNext.now?.let { now ->
                                Text(
                                    "Agora ${fmt.format(Date(now.start * 1000))} - ${now.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            nowNext.next?.let { next ->
                                Text(
                                    "Depois ${fmt.format(Date(next.start * 1000))} - ${next.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nowNextFor(
    epgIndex: Map<String, List<XmltvProgramme>>,
    channel: UiChannel
): EpgNowNext {
    if (epgIndex.isEmpty()) return EpgNowNext()
    val programmes = EpgLookup.findProgrammes(epgIndex, channel.tvgId, channel.name)
    return EpgLookup.nowNext(programmes, System.currentTimeMillis() / 1000)
}
