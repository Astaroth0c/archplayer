package br.archplayer.android

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.archplayer.core.live.EpgLookup
import br.archplayer.core.m3u.M3uParser
import br.archplayer.core.tvbox.TvBoxConfig
import br.archplayer.core.xciptv.XtreamCategory
import br.archplayer.core.xciptv.XtreamClient
import br.archplayer.core.xciptv.XtreamLiveStream
import br.archplayer.core.xmltv.XmltvProgramme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LiveRow(
    val name: String,
    val url: String,
    val tvgId: String? = null
)

@Composable
fun LiveScreen(
    client: XtreamClient?,
    epgIndex: Map<String, List<XmltvProgramme>>,
    tvboxConfig: TvBoxConfig?,
    onPlay: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var categories by remember { mutableStateOf(listOf<XtreamCategory>()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf(listOf<XtreamLiveStream>()) }
    var tvBoxGroups by remember { mutableStateOf(listOf<Pair<String, List<LiveRow>>>()) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var portalLoaded by remember { mutableStateOf(false) }
    var tvBoxLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(client) {
        if (client == null || portalLoaded) return@LaunchedEffect
        portalLoaded = true
        loading = true
        status = "Carregando categorias..."
        runCatching {
            withContext(Dispatchers.IO) {
                val cats = client.liveCategories()
                val firstId = cats.firstOrNull()?.categoryId
                val streams = client.liveStreams(firstId)
                cats to streams
            }
        }.onSuccess { (cats, streams) ->
            categories = cats
            selectedCategory = cats.firstOrNull()?.categoryId
            channels = streams
            status = "${channels.size} canais em ${cats.size} categorias"
        }.onFailure {
            status = "Erro ao carregar portal: ${it.message}"
        }
        loading = false
    }

    LaunchedEffect(client, tvboxConfig) {
        if (client != null || tvBoxLoaded || tvboxConfig == null || tvboxConfig.lives.isEmpty()) return@LaunchedEffect
        tvBoxLoaded = true
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val result = ArrayList<Pair<String, List<LiveRow>>>()
            tvboxConfig.lives.forEach { live ->
                if (live.type == 1 && live.groups.isNotEmpty()) {
                    live.groups.forEach { g ->
                        result.add(g.group to g.channels.map { LiveRow(it.name, it.urls.firstOrNull() ?: "", null) })
                    }
                } else if (live.url.startsWith("http")) {
                    runCatching {
                        val text = fetchText(live.url)
                        val trimmed = text.trimStart()
                        if (trimmed.startsWith("#EXTM3U")) {
                            val entries = M3uParser.parse(text).entries
                            if (entries.isNotEmpty()) {
                                entries.groupBy { it.groupTitle ?: "Geral" }.forEach { (g, list) ->
                                    result.add(g to list.map { LiveRow(it.name, it.url, it.tvgId) })
                                }
                            }
                        } else {
                            br.archplayer.core.live.LiveTxtParser().parse(text).forEach { group ->
                                result.add(group.name to group.channels.map { LiveRow(it.name, it.urls.firstOrNull() ?: "", null) })
                            }
                        }
                    }
                }
            }
            result
        }
        tvBoxGroups = loaded
        selectedGroup = loaded.firstOrNull()?.first
        status = if (loaded.isEmpty()) "Nenhum canal na interface TVBox"
        else "${loaded.sumOf { it.second.size }} canais carregados"
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        } else if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (client != null) {
            if (categories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.categoryId ?: it.categoryName.orEmpty() }) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.categoryId,
                            onClick = {
                                selectedCategory = cat.categoryId
                                loading = true
                                channels = emptyList()
                                scope.launch {
                                    val streams = runCatching {
                                        withContext(Dispatchers.IO) { client.liveStreams(cat.categoryId) }
                                    }.getOrNull() ?: emptyList()
                                    channels = streams
                                    loading = false
                                    status = if (streams.isEmpty()) "Categoria vazia" else "${streams.size} canais"
                                }
                            },
                            label = { Text(cat.categoryName ?: "?", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
            if (channels.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum canal nesta categoria.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(channels, key = { _, ch -> ch.streamId ?: ch.num ?: ch.name.orEmpty().hashCode() }) { index, ch ->
                        val name = ch.name ?: "Canal ${ch.streamId ?: index + 1}"
                        val programmes = EpgLookup.findProgrammes(epgIndex, ch.epgChannelId, name)
                        val nowNext = EpgLookup.nowNext(programmes, System.currentTimeMillis() / 1000)
                        val nowSlot = nowNext.now
                        val nextSlot = nowNext.next
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val id = ch.streamId
                                    if (id == null) {
                                        Toast.makeText(context, "Canal sem id de stream.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onPlay(client.streamUrl(br.archplayer.core.xciptv.XtreamStreamKind.LIVE, id, "m3u8"), null)
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                "${index + 1}. $name",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (nowSlot != null) {
                                Text(
                                    "Agora: ${nowSlot.title} (${fmt.format(Date(nowSlot.start * 1000))})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (nextSlot != null) {
                                Text(
                                    "Depois: ${nextSlot.title} (${fmt.format(Date(nextSlot.start * 1000))})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(160.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    tvBoxGroups.forEach { (group, _) ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
                            label = {
                                Text(
                                    group,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                val shown = tvBoxGroups.firstOrNull { it.first == selectedGroup }?.second ?: emptyList()
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(shown, key = { i, ch -> "$i-${ch.name}" }) { _, ch ->
                        val programmes = EpgLookup.findProgrammes(epgIndex, ch.tvgId, ch.name)
                        val nowNext = EpgLookup.nowNext(programmes, System.currentTimeMillis() / 1000)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (ch.url.isNotBlank()) onPlay(ch.url, null) }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(ch.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val nowSlotTv = nowNext.now
                            Text(
                                if (nowSlotTv != null) "Agora: ${nowSlotTv.title}" else "Sem EPG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
