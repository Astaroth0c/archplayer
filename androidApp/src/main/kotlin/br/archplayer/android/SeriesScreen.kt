package br.archplayer.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.archplayer.core.xciptv.XtreamCategory
import br.archplayer.core.xciptv.XtreamClient
import br.archplayer.core.xciptv.XtreamSeries
import br.archplayer.core.xciptv.XtreamSeriesInfo
import br.archplayer.core.xciptv.XtreamStreamKind
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SeriesScreen(client: XtreamClient, onPlay: (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf(listOf<XtreamCategory>()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var series by remember { mutableStateOf(listOf<XtreamSeries>()) }
    var detailInfo by remember { mutableStateOf<XtreamSeriesInfo?>(null) }
    var detailEntry by remember { mutableStateOf<XtreamSeries?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    fun loadCategory(categoryId: String?) {
        loading = true
        status = ""
        series = emptyList()
        scope.launch {
            val list = runCatching { withContext(Dispatchers.IO) { client.series(categoryId) } }
                .onFailure { status = "Erro: ${it.message}" }
                .getOrDefault(emptyList())
            series = list
            status = if (list.isEmpty()) "Nenhuma serie nesta categoria" else "${list.size} series"
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        loaded = true
        loading = true
        runCatching { withContext(Dispatchers.IO) { client.seriesCategories() } }
            .onSuccess { cats ->
                categories = cats
                loadCategory(cats.firstOrNull()?.categoryId)
            }
            .onFailure {
                status = "Erro ao carregar series: ${it.message}"
                loading = false
            }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.width(18.dp))
                Text(status.ifEmpty { "Carregando..." }, style = MaterialTheme.typography.bodySmall)
            }
        } else if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems(categories, key = { it.categoryId ?: it.categoryName.orEmpty() }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.categoryId,
                        onClick = {
                            selectedCategory = cat.categoryId
                            loadCategory(cat.categoryId)
                        },
                        label = { Text(cat.categoryName ?: "?", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }

        if (!loading && series.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(series, key = { it.seriesId ?: it.num ?: it.name.orEmpty().hashCode() }) { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val id = entry.seriesId ?: return@clickable
                                detailEntry = entry
                                detailInfo = null
                                selectedSeason = null
                                scope.launch {
                                    detailInfo = runCatching { withContext(Dispatchers.IO) { client.seriesInfo(id) } }.getOrNull()
                                    val seasons = detailInfo?.episodes?.keys?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                                    selectedSeason = seasons.minOrNull()
                                }
                            }
                    ) {
                        AsyncImage(
                            model = entry.cover,
                            contentDescription = entry.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .background(Color(0x33204275), RoundedCornerShape(8.dp))
                        )
                        Text(
                            entry.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        entry.genre?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
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

    detailEntry?.let { entry ->
        val info = detailInfo?.info
        val seasons = detailInfo?.episodes?.keys?.mapNotNull { it.toIntOrNull() }?.sorted() ?: emptyList()
        val episodes = detailInfo?.episodes?.get(selectedSeason?.toString() ?: seasons.firstOrNull()?.toString()) ?: emptyList()
        AlertDialog(
            onDismissRequest = { detailInfo = null; detailEntry = null },
            confirmButton = {
                TextButton(onClick = { detailInfo = null; detailEntry = null }) { Text("Fechar") }
            },
            title = { Text(entry.name ?: "Serie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detailInfo == null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.width(24.dp))
                        }
                    }
                    info?.let {
                        Row(verticalAlignment = Alignment.Top) {
                            AsyncImage(
                                model = it.cover ?: entry.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(110.dp).aspectRatio(0.7f).background(Color(0x33204275), RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                it.rating?.let { r -> Text("Nota: $r", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                                it.genre?.let { g -> Text(g, style = MaterialTheme.typography.bodySmall) }
                                it.releaseDate?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        it.plot?.let { p -> Text(p.take(400), style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                    }
                    if (seasons.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems(seasons) { season ->
                                FilterChip(
                                    selected = selectedSeason == season,
                                    onClick = { selectedSeason = season },
                                    label = { Text(if (season == 0) "Especiais" else "T$season") }
                                )
                            }
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(episodes, key = { it.id ?: it.episodeNum ?: it.title.orEmpty() }) { ep ->
                            TextButton(
                                onClick = {
                                    val id = ep.id?.toIntOrNull() ?: return@TextButton
                                    onPlay(
                                        client.streamUrl(XtreamStreamKind.SERIES, id, ep.containerExtension),
                                        XtreamClient.USER_AGENT
                                    )
                                    detailInfo = null
                                    detailEntry = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "T${ep.season ?: selectedSeason ?: "?"} E${ep.episodeNum ?: "?"} - ${ep.title ?: "Episodio"}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (episodes.isEmpty() && detailInfo != null) {
                            item { Text("Nenhum episodio nesta temporada.", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        )
    }
}
