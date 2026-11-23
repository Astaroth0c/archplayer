package br.archplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.archplayer.core.live.EpgLookup
import br.archplayer.core.xciptv.XtreamCategory
import br.archplayer.core.xciptv.XtreamClient
import br.archplayer.core.xciptv.XtreamCredentials
import br.archplayer.core.xciptv.XtreamStreamKind
import br.archplayer.core.xmltv.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

object DesktopPrefs {
    private val node = Preferences.userNodeForPackage(DesktopPrefs::class.java)
    fun get(key: String): String = node.get(key, "")
    fun put(key: String, value: String) = node.put(key, value)
    fun remove(key: String) = node.remove(key)
}

const val APP_VERSION = "1.4.0"

object UpdateCheck {
    private const val API = "https://api.github.com/repos/Astaroth0c/archplayer/releases/latest"

    fun latestVersion(): String? = runCatching {
        val c = java.net.URL(API).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "ArchPlayer/$APP_VERSION")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        Regex("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").find(body)?.groupValues?.get(1).orEmpty()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai > bi
        }
        return false
    }

    fun openDownloadPage() {
        runCatching {
            java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/Astaroth0c/archplayer/releases/latest"))
        }
    }
}

fun openExternal(url: String) {
    val os = System.getProperty("os.name").lowercase()
    if (os.contains("win")) {
        val candidates = listOf(
            listOf("mpv", url),
            listOf("C:\\Program Files\\mpv\\mpv.exe", url),
            listOf("C:\\Program Files (x86)\\mpv\\mpv.exe", url),
            listOf("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe", url),
            listOf("C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe", url),
            listOf("C:\\Program Files\\Windows Media Player\\wmplayer.exe", url)
        )
        candidates.forEach { cmd ->
            val path = cmd.first()
            val found = path == "mpv" || java.io.File(path).exists()
            if (found) {
                runCatching {
                    ProcessBuilder(cmd).start()
                    return
                }
            }
        }
        runCatching { ProcessBuilder(listOf("cmd", "/c", "start", "", url)).start() }
        return
    }
    runCatching {
        ProcessBuilder(listOf("mpv", "--fullscreen=no", url)).start()
        return
    }
    val cmd = when {
        os.contains("mac") -> listOf("open", url)
        else -> listOf("xdg-open", url)
    }
    runCatching { ProcessBuilder(cmd).start() }
}

class DSession(val client: XtreamClient, val creds: XtreamCredentials, val status: String?, val expDate: String?)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ArchPlayer",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF4C8DFF),
                background = Color(0xFF0B0E13),
                surface = Color(0xFF11151F),
                surfaceVariant = Color(0xFF1A2233),
                onSurfaceVariant = Color(0xFF9AA7BD),
                error = Color(0xFFFF6B6B)
            )
        ) {
            DesktopApp()
        }
    }
}

private enum class DScreen { Splash, Login, Main }
private enum class DTab(val label: String) { LIVE("TV ao vivo"), MOVIES("Filmes"), SERIES("Séries"), SETTINGS("Config") }

@Composable
fun DesktopApp() {
    var screen by remember { mutableStateOf(DScreen.Splash) }
    var session by remember { mutableStateOf<DSession?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }
    var epgIndex by remember { mutableStateOf(emptyMap<String, List<br.archplayer.core.xmltv.XmltvProgramme>>()) }
    var updateVersion by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val checkUpdate: () -> Unit = {
        Thread {
            val latest = UpdateCheck.latestVersion()
            if (latest != null && UpdateCheck.isNewer(latest, APP_VERSION)) {
                updateVersion = latest
            }
        }.start()
    }

    fun doConnect(host: String, user: String, pass: String) {
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            loginError = "Preencha host, usuario e senha."
            return
        }
        loggingIn = true
        loginError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val creds = XtreamCredentials(host.trim(), user.trim(), pass.trim())
                    val client = XtreamClient(creds)
                    val hs = client.handshake()
                    if (hs.userInfo == null) error("Portal nao respondeu. Verifique host, usuario e senha.")
                    client to hs
                }
            }
            result.onSuccess { (client, hs) ->
                session = DSession(client, XtreamCredentials(host.trim(), user.trim(), pass.trim()), hs.userInfo?.status, hs.userInfo?.expDate)
                DesktopPrefs.put("xtream_host", host.trim())
                DesktopPrefs.put("xtream_user", user.trim())
                DesktopPrefs.put("xtream_pass", pass.trim())
                screen = DScreen.Main
                Thread {
                    val idx = runCatching {
                        EpgLookup.byChannelId(XmltvParser.parse(Fetch.fetch(client.epgUrl())))
                    }.getOrNull() ?: emptyMap()
                    if (idx.isNotEmpty()) epgIndex = idx
                }.start()
            }.onFailure {
                loginError = it.message ?: "Falha na conexao"
            }
            loggingIn = false
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val latest = runCatching { UpdateCheck.latestVersion() }.getOrNull()
            if (latest != null && UpdateCheck.isNewer(latest, APP_VERSION)) {
                updateVersion = latest
            }
        }
        kotlinx.coroutines.delay(1400)
        val h = DesktopPrefs.get("xtream_host")
        val u = DesktopPrefs.get("xtream_user")
        val p = DesktopPrefs.get("xtream_pass")
        if (h.isBlank() || u.isBlank() || p.isBlank()) {
            screen = DScreen.Login
        } else {
            doConnect(h, u, p)
        }
    }

    if (updateVersion.isNotEmpty()) {
        Dialog(onDismissRequest = { updateVersion = "" }) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nova versao $updateVersion disponivel", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Voce esta usando $APP_VERSION.", style = MaterialTheme.typography.bodyMedium)
                    Text("Baixe o novo instalador (.exe) e reinstale em cima da versao atual.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { UpdateCheck.openDownloadPage() }) { Text("Abrir download") }
                        OutlinedButton(onClick = { updateVersion = "" }) { Text("Depois") }
                    }
                }
            }
        }
    }

    when (screen) {
        DScreen.Splash -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ArchPlayer", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                CircularProgressIndicator()
            }
        }
        DScreen.Login -> LoginUi(loginError, loggingIn, ::doConnect)
        DScreen.Main -> {
            val s = session
            if (s == null) {
                screen = DScreen.Login
            } else {
                DesktopShell(s, epgIndex, onCheckUpdate = checkUpdate, onLogout = {
                    DesktopPrefs.remove("xtream_host"); DesktopPrefs.remove("xtream_user"); DesktopPrefs.remove("xtream_pass")
                    session = null
                    screen = DScreen.Login
                })
            }
        }
    }
}

private object Fetch {
    fun fetch(url: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "ArchPlayer/1.0")
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}

@Composable
private fun LoginUi(error: String, loading: Boolean, onLogin: (String, String, String) -> Unit) {
    var host by remember { mutableStateOf(DesktopPrefs.get("xtream_host")) }
    var user by remember { mutableStateOf(DesktopPrefs.get("xtream_user")) }
    var pass by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ArchPlayer", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Entre com seu portal IPTV (Xtream Codes)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(host, { host = it }, label = { Text("Servidor (http://host:porta)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Senha") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onLogin(host, user, pass) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Conectando..." else "Entrar")
            }
        }
    }
}

@Composable
private fun DesktopShell(
    session: DSession,
    epgIndex: Map<String, List<br.archplayer.core.xmltv.XmltvProgramme>>,
    onCheckUpdate: () -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf(DTab.LIVE) }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.width(200.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ArchPlayer", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            DTab.entries.forEach { item ->
                if (tab == item) {
                    Button(onClick = { tab = item }, modifier = Modifier.fillMaxWidth()) { Text(item.label) }
                } else {
                    OutlinedButton(onClick = { tab = item }, modifier = Modifier.fillMaxWidth()) { Text(item.label) }
                }
            }
            Spacer(Modifier.size(24.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }
            Text("mpv recomendado para playback.\nURL aberta no player padrao caso mpv nao esteja instalado.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            when (tab) {
                DTab.LIVE -> DesktopLive(session, epgIndex)
                DTab.MOVIES -> DesktopVod(session)
                DTab.SERIES -> DesktopSeries(session)
                DTab.SETTINGS -> DesktopSettings(session, onCheckUpdate)
            }
        }
    }
}

@Composable
private fun DesktopLive(session: DSession, epgIndex: Map<String, List<br.archplayer.core.xmltv.XmltvProgramme>>) {
    val client = session.client
    var categories by remember { mutableStateOf(listOf<XtreamCategory>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var streams by remember { mutableStateOf(listOf<br.archplayer.core.xciptv.XtreamLiveStream>()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(categoryId: String?) {
        loading = true
        scope.launch {
            streams = runCatching { withContext(Dispatchers.IO) { client.liveStreams(categoryId) } }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { client.liveCategories() } }
            .onSuccess { categories = it; load(it.firstOrNull()?.categoryId) }
            .onFailure { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selected == cat.categoryId,
                    onClick = { selected = cat.categoryId; load(cat.categoryId) },
                    label = { Text(cat.categoryName ?: "?") }
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        LazyColumn {
            items(streams, key = { it.streamId ?: it.num ?: 0 }) { ch ->
                val name = ch.name ?: "Canal ${ch.streamId}"
                val programmes = EpgLookup.findProgrammes(epgIndex, ch.epgChannelId, name)
                val nowNext = EpgLookup.nowNext(programmes, System.currentTimeMillis() / 1000)
                val nowSlot = nowNext.now
                Column(
                    Modifier.fillMaxWidth().clickable {
                        ch.streamId?.let { id -> openExternal(client.streamUrl(XtreamStreamKind.LIVE, id)) }
                    }.padding(vertical = 6.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    if (nowSlot != null) {
                        Text("Agora: ${nowSlot.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopVod(session: DSession) {
    val client = session.client
    var categories by remember { mutableStateOf(listOf<XtreamCategory>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var streams by remember { mutableStateOf(listOf<br.archplayer.core.xciptv.XtreamVodStream>()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(categoryId: String?) {
        loading = true
        scope.launch {
            streams = runCatching { withContext(Dispatchers.IO) { client.vodStreams(categoryId) } }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { client.vodCategories() } }
            .onSuccess { categories = it; load(it.firstOrNull()?.categoryId) }
            .onFailure { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.take(20).forEach { cat ->
                FilterChip(
                    selected = selected == cat.categoryId,
                    onClick = { selected = cat.categoryId; load(cat.categoryId) },
                    label = { Text(cat.categoryName ?: "?") }
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        LazyColumn {
            items(streams, key = { it.streamId ?: it.num ?: 0 }) { movie ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        movie.streamId?.let { id ->
                            openExternal(client.streamUrl(XtreamStreamKind.VOD, id, movie.containerExtension))
                        }
                    }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(movie.name ?: "", style = MaterialTheme.typography.bodyLarge)
                    movie.rating5Based?.takeIf { it > 0 }?.let {
                        Text("%.1f".format(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSeries(session: DSession) {
    val client = session.client
    var categories by remember { mutableStateOf(listOf<XtreamCategory>()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var series by remember { mutableStateOf(listOf<br.archplayer.core.xciptv.XtreamSeries>()) }
    var chosen by remember { mutableStateOf<br.archplayer.core.xciptv.XtreamSeries?>(null) }
    var info by remember { mutableStateOf<br.archplayer.core.xciptv.XtreamSeriesInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(categoryId: String?) {
        loading = true
        chosen = null
        info = null
        scope.launch {
            series = runCatching { withContext(Dispatchers.IO) { client.series(categoryId) } }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { client.seriesCategories() } }
            .onSuccess { categories = it; load(it.firstOrNull()?.categoryId) }
            .onFailure { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.take(20).forEach { cat ->
                FilterChip(
                    selected = selectedCat == cat.categoryId,
                    onClick = { selectedCat = cat.categoryId; load(cat.categoryId) },
                    label = { Text(cat.categoryName ?: "?") }
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.width(320.dp)) {
                items(series, key = { it.seriesId ?: 0 }) { entry ->
                    Text(
                        entry.name ?: "",
                        modifier = Modifier.fillMaxWidth().clickable {
                            chosen = entry
                            val id = entry.seriesId ?: return@clickable
                            scope.launch {
                                info = runCatching { withContext(Dispatchers.IO) { client.seriesInfo(id) } }.getOrNull()
                            }
                        }.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            chosen?.let { entry ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.name ?: "Serie", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    info?.info?.plot?.let { Text(it.take(300), style = MaterialTheme.typography.bodySmall, maxLines = 4) }
                    info?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 }?.forEach { season ->
                        Text("Temporada $season", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        info?.episodes?.get(season)?.forEach { ep ->
                            Text(
                                "E${ep.episodeNum ?: "?"} - ${ep.title ?: "Episodio"}",
                                modifier = Modifier.fillMaxWidth().clickable {
                                    ep.id?.toIntOrNull()?.let { id ->
                                        openExternal(client.streamUrl(XtreamStreamKind.SERIES, id, ep.containerExtension))
                                    }
                                }.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSettings(session: DSession, onCheckUpdate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
        Text("Conta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Servidor: ${session.creds.host}", style = MaterialTheme.typography.bodyMedium)
        Text("Usuario: ${session.creds.username}", style = MaterialTheme.typography.bodyMedium)
        session.status?.let { Text("Status: $it", style = MaterialTheme.typography.bodyMedium) }
        session.expDate?.toLongOrNull()?.let {
            Text("Expira em: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(it * 1000))}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Reproducao: live/movie/series abrem no mpv (recomendado), VLC ou Windows Media Player.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Versao: $APP_VERSION",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCheckUpdate) { Text("Verificar atualizacoes") }
            OutlinedButton(onClick = { UpdateCheck.openDownloadPage() }) { Text("Baixar nova versao") }
        }
        Text(
            "Dica: para os melhores codecs, instale o VLC (videolan.org) ou o mpv (mpv.io).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
