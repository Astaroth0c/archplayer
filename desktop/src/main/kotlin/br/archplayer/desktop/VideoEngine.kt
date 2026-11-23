package br.archplayer.desktop

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * Motor de video/proprio do ArchPlayer (desktop):
 * FFmpeg embutido faz demux + decode (HLS/TS/MP4, H.264/H.265/MPEG, AAC/EAC3/MP3)
 * e desenha os quadros direto na janela, sem depender de player externo.
 */
class VideoEngine(private val url: String) {

    @Volatile
    var stopped = false

    @Volatile
    var muted = false

    /** Callback chamado no thread do motor a cada quadro decodificado. */
    fun start(
        onFrame: (BufferedImage) -> Unit,
        onStatus: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Thread {
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
            val grabber = FFmpegFrameGrabber(url)
            val opened = runCatching {
                grabber.setOption("user_agent", "ArchPlayer/$APP_VERSION")
                grabber.setOption("reconnect", "1")
                grabber.setOption("reconnect_streamed", "1")
                grabber.setOption("rw_timeout", "15000000")
                grabber.start()
            }.isSuccess
            if (!opened) {
                onFailure("Falha ao abrir o stream. Verifique a internet ou use 'Player externo'.")
                return@Thread
            }
            onStatus(
                "Fonte: ${grabber.imageWidth}x${grabber.imageHeight} @ ${grabber.frameRate.toInt()}fps " +
                    "| audio ${grabber.sampleRate}Hz ${grabber.audioChannels}ch"
            )

            val audioFormat = AudioFormat(44_100f, 16, 2, true, false)
            val line = runCatching {
                AudioSystem.getSourceDataLine(audioFormat).apply {
                    open(audioFormat, 44_100 * 4)
                    start()
                }
            }.getOrNull()

            val converter = Java2DFrameConverter()
            var lastFrameMs = 0L
            while (!stopped) {
                val frame = runCatching { grabber.grab() }.getOrNull() ?: break

                frame.samples?.let { samples ->
                    if (!muted) writeSamples(samples, line)
                }

                if (frame.image != null) {
                    val img = runCatching { converter.convert(frame) }.getOrNull()
                    if (img != null) {
                        // sincroniza com o audio (quando existe) ou limita ao framerate da fonte
                        val targetMs = 900 / maxOf(1, grabber.frameRate.toInt().coerceAtLeast(1))
                        val now = System.currentTimeMillis()
                        val minGap = if (line != null && !muted) 12L else targetMs.toLong()
                        val elapsed = now - lastFrameMs
                        if (elapsed in 1 until minGap) {
                            @Suppress("NAME_SHADOWING")
                            val wait = minGap - elapsed
                            Thread.sleep(wait)
                        }
                        lastFrameMs = System.currentTimeMillis()
                        onFrame(img)
                    }
                }
            }
            runCatching { line?.stop(); line?.close() }
            runCatching { grabber.stop(); grabber.release() }
        }.apply {
            name = "arch-video"
            isDaemon = true
            start()
        }
    }

    private fun writeSamples(samples: Array<java.nio.Buffer>?, line: javax.sound.sampled.SourceDataLine?) {
        if (samples == null || line == null) return
        val pcm = toPcm16(samples) ?: return
        if (pcm.isNotEmpty()) line.write(pcm, 0, pcm.size)
    }

    /** Converte samples de audio (ShortBuffer packed ou FloatBuffer plano/intercalado) para PCM16 little-endian. */
    private fun toPcm16(samples: Array<java.nio.Buffer>): ByteArray? {
        val first = samples.firstOrNull() ?: return null
        return when (first) {
            is ShortBuffer -> {
                val out = ByteArray(first.remaining() * 2)

                for (i in 0 until first.remaining()) {
                    val v = first.get()
                    out[2 * i] = (v.toInt() and 0xFF).toByte()
                    out[2 * i + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                }
                out
            }
            is FloatBuffer -> {
                // planar: um buffer por canal -> intercalar e escalar
                val channels = samples.size
                val perChannel = first.remaining()
                val total = perChannel * channels

                val out = ByteArray(total * 2)
                val chans = samples.map { it as FloatBuffer }
                var o = 0

                for (i in 0 until perChannel) {
                    for (c in 0 until channels) {
                        val v = (chans[c].get(i) * 32767f).toInt().coerceIn(-32768, 32767)

                        out[o++] = (v and 0xFF).toByte()
                        out[o++] = ((v shr 8) and 0xFF).toByte()
                    }
                }
                out
            }
            else -> null
        }
    }
}
