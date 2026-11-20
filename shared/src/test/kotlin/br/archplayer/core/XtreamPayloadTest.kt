package br.archplayer.core

import br.archplayer.core.xciptv.XtreamClient
import br.archplayer.core.xciptv.XtreamCredentials
import br.archplayer.core.xciptv.XtreamHandshake
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data class Hs(
    val user_info: br.archplayer.core.xciptv.XtreamUserInfo? = null,
    val server_info: br.archplayer.core.xciptv.XtreamServerInfo? = null
)

class XtreamPayloadTest {

    // payload real de um portal XUI.one (estrutura identica ao player em producao)
    private val xuiPayload = """
        {
          "user_info": {
            "username": "55718733",
            "password": "48659005",
            "message": "Welcome to XUI.one",
            "auth": 1,
            "status": "Active",
            "exp_date": "1795204645",
            "is_trial": "1",
            "active_cons": 0,
            "created_at": "1795190247",
            "max_connections": "2",
            "allowed_output_formats": ["m3u8", "ts"]
          },
          "server_info": {
            "xui": true,
            "version": "1.5.5",
            "revision": 2,
            "url": "x99.lat",
            "port": "80",
            "https_port": "443",
            "server_protocol": "http",
            "rtmp_port": "8880",
            "timestamp_now": 1795195099,
            "time_now": "2026-11-20 14:18:19",
            "timezone": "America\/Sao_Paulo"
          }
        }
    """.trimIndent()

    @Test
    fun parseXuiHandshake() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }
        val hs = json.decodeFromString<Hs>(xuiPayload)
        val info = hs.user_info
        assertNotNull(info)
        assertEquals("Active", info.status)
        assertEquals(1, info.auth)
        assertEquals("2", info.maxConnections)
        assertTrue(hs.server_info != null)
    }

    @Test
    fun streamUrls() {
        val client = XtreamClient(XtreamCredentials("http://us8.lat", "u", "p"))
        assertTrue(client.streamUrl(br.archplayer.core.xciptv.XtreamStreamKind.LIVE, 42).endsWith("/live/u/p/42"))
        assertTrue(
            client.streamUrl(br.archplayer.core.xciptv.XtreamStreamKind.SERIES, 7, "mp4").endsWith("/series/u/p/7.mp4")
        )
    }

    @Test
    fun emptyOrGarbageResponsesFailSafe() {
        assertEquals("[]", XtreamClient.jsonArrayOrEmpty("<html>erro</html>"))
        assertEquals("{}", XtreamClient.jsonObjectOrEmpty(""))
    }
}
