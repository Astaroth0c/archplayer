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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.archplayer.core.cms.CmsCategory
import br.archplayer.core.cms.CmsClient
import br.archplayer.core.cms.CmsDetail
import br.archplayer.core.cms.CmsVideo
import br.archplayer.core.cms.playlists
import br.archplayer.core.tvbox.Site
import br.archplayer.core.tvbox.TvBoxConfig
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private data class SourceWithApi(val site: Site, val client: CmsClient)

@Composable
fun SourcesScreen(config: TvBoxConfig?, onPlay: (String, String?) -> Unit) {
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Carregue uma interface na aba Config")
        }
        return
    }

    val cmsSites = remember(config) {
        config.sites.filter { it.type == 1 && it.api.isNotBlank() }
    }
    if (cmsSites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma fonte CMS (type 1) nesta interface.\n" +
                    "Spiders (type 3) chegarem na fase 3."
            )
        }
        return
    }

    var selectedSite by remember { mutableStateOf(cmsSites.firstOrNull()) }
    var activeClient by remember { mutableStateOf<SourceWithApi?>(null) }
    var categories by remember { mutableStateOf(listOf<CmsCategory>()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf(listOf<CmsVideo>()) }
    var page by remember { mutableStateOf(1) }
    var pageCount by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CmsDetail?>(null) }
    var status by remember { mutableStateOf("") }

    suspend fun loadList(site: SourceWithApi, category: String?, targetPage: Int) {
        loading = true
        status = ""
        runCatching {
            site.client.videos(category, targetPage)
        }.onSuccess { (list, pages) ->
            videos = if (targetPage == 1) list else videos + list
            page = targetPage
            pageCount = pages.coerceAtLeast(1)
        }.onFailure {
            status = "Erro: ${it.message}"
        }
        loading = false
    }

    LaunchedEffect(selectedSite) {
        activeClient = selectedSite?.let { SourceWithApi(it, CmsClient(it.api)) }
        categories = emptyList()
        videos = emptyList()
        selectedCategory = null
        activeClient?.let { client ->
            runCatching { client.client.categories() }
                .onSuccess { categories = it }
                .onFailure { status = "Sem categorias: ${it.message}" }
            loadList(client, null, 1)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            Column {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(selectedSite?.name ?: "Escolher fonte")
                }
                if (expanded) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        rowItems(cmsSites) { site ->
                            FilterChip(
                                selected = site == selectedSite,
                                onClick = { expanded = false; selectedSite = site },
                                label = { Text(site.name) }
                            )
                        }
                    }
                }
            }
        }

        if (selectedSite?.searchable == true) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar nesta fonte") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    scopeFetch {
                        val client = activeClient ?: return@scopeFetch
                        searchMode = searchQuery.isNotBlank()
                        if (searchQuery.isNotBlank()) {
                            loading = true
                            runCatching { client.client.search(searchQuery, 1) }
                                .onSuccess { (list, pages) -> videos = list; page = 1; pageCount = pages }
                                .onFailure { status = "Erro: ${it.message}" }
                            loading = false
                        } else {
                            loadList(client, selectedCategory, 1)
                        }
                    }
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar")
                }
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category.typeId,
                        onClick = {
                            selectedCategory = category.typeId
                            searchMode = false
                            scopeFetch { activeClient?.let { loadList(it, category.typeId, 1) } }
                        },
                        label = { Text(category.typeName) }
                    )
                }
            }
        }

        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

        if (loading && videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { it.vodId }) { video ->
                    VideoCard(video = video) {
                        scopeFetch {
                            val client = activeClient ?: return@scopeFetch
                            runCatching { client.client.detail(video.vodId) }
                                .onSuccess { detail = it }
                                .onFailure { status = "Erro: ${it.message}" }
                        }
                    }
                }
                if (!searchMode && page < pageCount) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = { scopeFetch { activeClient?.let { loadList(it, selectedCategory, page + 1) } } },
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text(if (loading) "Carregando..." else "Carregar mais (pg $page/$pageCount)")
                        }
                    }
                }
            }
        }
    }

    detail?.let { d ->
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("Fechar") }
            },
            title = { Text(d.vodName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model = d.vodPic,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(0.7f)
                                .background(Color(0x33204275), RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            listOfNotNull(
                                d.vodClass, d.vodArea, d.vodYear, d.vodRemarks
                            ).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            d.vodRemarks?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                            d.vodDirector?.let { Text("Diretor: ${it.take(80)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            d.vodActor?.let { Text("Elenco: ${it.take(120)}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    d.vodContent?.let { Text(it.take(400), style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                    val playlists = d.playlists()
                    playlists.forEachIndexed { pi, playlist ->
                        Text(playlist.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        playlist.episodes.forEach { episode ->
                            TextButton(onClick = { onPlay(episode.url, null) }) {
                                Text("- ${episode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (playlists.isEmpty()) Text("Nenhum episodio encontrado.", style = MaterialTheme.typography.bodySmall)
                }
            }
    )
}
}

private fun scopeFetch(block: suspend () -> Unit): kotlinx.coroutines.Job =
    GlobalScope.launch(Dispatchers.Main) {
        block()
    }

@Composable
private fun VideoCard(video: CmsVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = video.vodPic,
            contentDescription = video.vodName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(Color(0x33204275), RoundedCornerShape(8.dp))
        )
        Text(
            video.vodName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        video.vodRemarks?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
