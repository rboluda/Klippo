package es.rboluda.klippo.edicion

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

// Medidas del vídeo ya girado (como se ve en pantalla y como lo procesa FFmpeg)
data class MedidasVideo(
    val duracionMs: Long,
    val ancho: Int,
    val alto: Int,
    // Fotogramas por segundo (30 si el archivo no lo dice)
    val fps: Float = FPS_POR_DEFECTO,
) {
    val aspecto: Float get() = if (ancho > 0 && alto > 0) ancho.toFloat() / alto else 0f

    // Lo que dura un fotograma, redondeado hacia arriba (33 ms a 30 fps)
    val fotogramaMs: Long get() = kotlin.math.ceil(1000.0 / fps).toLong()

    companion object {
        // Lee el propio archivo (sin red); bloquea, llamar fuera del hilo principal
        fun leer(video: File): MedidasVideo {
            val lector = MediaMetadataRetriever()
            return try {
                lector.setDataSource(video.absolutePath)
                fun entero(clave: Int) = lector.extractMetadata(clave)?.toLongOrNull() ?: 0L
                var ancho = entero(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
                var alto = entero(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
                // Los vídeos grabados en vertical a veces vienen girados por metadatos
                val rotacion = entero(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
                if (rotacion == 90 || rotacion == 270) ancho = alto.also { alto = ancho }
                MedidasVideo(entero(MediaMetadataRetriever.METADATA_KEY_DURATION), ancho, alto, leerFps(video))
            } catch (e: Exception) {
                MedidasVideo(0, 0, 0)
            } finally {
                lector.release()
            }
        }

        private const val FPS_POR_DEFECTO = 30f

        // Fotogramas por segundo de la pista de vídeo, tal como los declara el contenedor
        private fun leerFps(video: File): Float {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(video.absolutePath)
                (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                    ?.takeIf { it.containsKey(MediaFormat.KEY_FRAME_RATE) }
                    ?.let { f -> runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                        .recoverCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull() }
                    ?.takeIf { it in 1f..240f }
                    ?: FPS_POR_DEFECTO
            } catch (e: Exception) {
                FPS_POR_DEFECTO
            } finally {
                extractor.release()
            }
        }
    }
}
