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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
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
import br.archplayer.core.xciptv.XtreamStreamKind
import br.archplayer.core.xciptv.XtreamVodInfo
import br.archplayer.core.xciptv.XtreamVodStream
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoviesScreen(client: XtreamClient, onPlay: (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf(listOf<XtreamCategory>()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var movies by remember { mutableStateOf(listOf<XtreamVodStream>()) }
    var detail by remember { mutableStateOf<XtreamVodInfo?>(null) }
    var detailEntry by remember { mutableStateOf<XtreamVodStream?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    fun loadCategory(categoryId: String?) {
        loading = true
        status = ""
        movies = emptyList()
        scope.launch {
            val list = runCatching { withContext(Dispatchers.IO) { client.vodStreams(categoryId) } }
                .onFailure { status = "Erro: ${it.message}" }
                .getOrDefault(emptyList())
            movies = list
            status = if (list.isEmpty()) "Nenhum filme nesta categoria" else "${list.size} filmes"
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        loaded = true
        loading = true
        runCatching { withContext(Dispatchers.IO) { client.vodCategories() } }
            .onSuccess { cats ->
                categories = cats
                loadCategory(cats.firstOrNull()?.categoryId)
            }
            .onFailure {
                status = "Erro ao carregar filmes: ${it.message}"
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

        if (!loading && movies.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(movies, key = { it.streamId ?: it.num ?: it.name.orEmpty().hashCode() }) { movie ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val id = movie.streamId ?: return@clickable
                                detailEntry = movie
                                scope.launch {
                                    detail = runCatching { withContext(Dispatchers.IO) { client.vodInfo(id) } }.getOrNull()
                                }
                            }
                    ) {
                        AsyncImage(
                            model = movie.streamIcon,
                            contentDescription = movie.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .background(Color(0x33204275), RoundedCornerShape(8.dp))
                        )
                        Text(
                            movie.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        movie.rating5Based?.takeIf { it > 0 }?.let {
                            Text(
                                "%.1f".format(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    detailEntry?.let { entry ->
        val info = detail?.info
        AlertDialog(
            onDismissRequest = { detail = null; detailEntry = null },
            confirmButton = {
                Button(
                    onClick = {
                        val id = entry.streamId ?: return@Button
                        onPlay(
                            client.streamUrl(XtreamStreamKind.VOD, id, entry.containerExtension ?: detail?.movieData?.containerExtension),
                            XtreamClient.USER_AGENT
                        )
                        detail = null
                        detailEntry = null
                    }
                ) { Text("Assistir") }
            },
            dismissButton = {
                TextButton(onClick = { detail = null; detailEntry = null }) { Text("Fechar") }
            },
            title = { Text(entry.name ?: "Filme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detail == null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.width(24.dp))
                        }
                    }
                    info?.let {
                        Row(verticalAlignment = Alignment.Top) {
                            AsyncImage(
                                model = it.coverBig ?: entry.streamIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(110.dp).aspectRatio(0.7f).background(Color(0x33204275), RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                it.rating?.let { r -> Text("Nota: $r", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                                it.genre?.let { g -> Text(g, style = MaterialTheme.typography.bodySmall) }
                                it.releaseDate?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall) }
                                it.duration?.let { d -> Text("Duracao: $d", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        it.plot?.let { p -> Text(p.take(500), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                        it.castRaw?.let { c -> Text("Elenco: ${c.take(150)}", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        )
    }
}
