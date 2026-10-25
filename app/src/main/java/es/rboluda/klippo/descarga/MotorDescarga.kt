package es.rboluda.klippo.descarga

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import es.rboluda.klippo.R
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.sesion.Plataforma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Estado del motor (yt-dlp + FFmpeg) visible para la UI
sealed interface EstadoMotor {
    data object Iniciando : EstadoMotor
    data class Listo(val version: String?) : EstadoMotor
    data class Error(val mensaje: String) : EstadoMotor
}

enum class ResultadoActualizacion { ACTUALIZADO, YA_AL_DIA, ERROR }

// Envoltorio sobre youtubedl-android para que el resto de la app no dependa de él
class MotorDescarga(private val context: Context) {

    private val _estado = MutableStateFlow<EstadoMotor>(EstadoMotor.Iniciando)
    val estado: StateFlow<EstadoMotor> = _estado.asStateFlow()

    // La primera vez descomprime Python y yt-dlp (varios segundos): siempre en segundo plano
    fun iniciar(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _estado.value = try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                EstadoMotor.Listo(YoutubeDL.getInstance().version(context))
            } catch (e: Exception) {
                EstadoMotor.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // Suspende hasta que el motor termine de arrancar; devuelve true si está listo
    suspend fun esperarListo(): Boolean =
        estado.first { it !is EstadoMotor.Iniciando } is EstadoMotor.Listo

    // Descarga el vídeo en [carpeta] y devuelve los archivos resultantes, en orden: uno
    // normalmente, varios si es un carrusel de Instagram o X (video-1.mp4, video-2.mp4…).
    // Bloquea hasta terminar; se cancela con cancelar(id), que lanza CanceledException.
    suspend fun descargar(
        id: String,
        url: String,
        carpeta: File,
        // Cookies de sesión (Instagram, X) si el usuario ha iniciado sesión
        cookies: File?,
        calidad: Calidad,
        alProgreso: (progreso: Float, etaSegundos: Long) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        check(esperarListo()) { context.getString(R.string.motor_no_listo) }
        carpeta.mkdirs()
        val formatoNormal = if (calidad.soloAudio) null else formato(calidad.alturaMax, esTikTok(url))
        ejecutar(id, url, carpeta, cookies, calidad, formatoNormal, alProgreso)
        var archivos = archivosDescargados(carpeta)

        // Comprobación de audio: hay formatos que dicen tener sonido y no lo tienen (algunos
        // H.265 de TikTok, yt-dlp issues 16622 y 17372). Si un vídeo llega mudo, se repite la
        // descarga con un formato de imagen y sonido juntos en H.264, que siempre lo trae.
        if (!calidad.soloAudio && archivos.any { !tieneAudio(it) }) {
            archivos.forEach { it.delete() }
            File(carpeta, ARCHIVO_TITULO).delete()
            ejecutar(id, url, carpeta, cookies, calidad, formatoConAudio(calidad.alturaMax), alProgreso)
            archivos = archivosDescargados(carpeta)
        }
        archivos
    }

    // Una ejecución de yt-dlp. [formatoVideo] null = solo audio
    private fun ejecutar(
        id: String,
        url: String,
        carpeta: File,
        cookies: File?,
        calidad: Calidad,
        formatoVideo: String?,
        alProgreso: (progreso: Float, etaSegundos: Long) -> Unit,
    ) {
        val peticion = YoutubeDLRequest(url).apply {
            if (formatoVideo == null) {
                // M4A (AAC) tal cual si existe; si no, FFmpeg lo convierte
                addOption("-f", "bestaudio[ext=m4a]/bestaudio")
                addOption("-x")
                addOption("--audio-format", "m4a")
                // La imagen del vídeo sirve de miniatura (el audio no tiene fotogramas)
                addOption("--write-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                addOption("-o", "thumbnail:" + File(carpeta, "miniatura.%(ext)s").absolutePath)
            } else {
                addOption("-f", formatoVideo)
                // Si solo hay WebM/MKV, se cambia el contenedor a MP4 sin recodificar
                addOption("--remux-video", "mp4")
            }
            if (Plataforma.deUrl(url) != null) {
                // Instagram y X: un carrusel es una "lista"; se bajan todos sus vídeos
                // (yt-dlp no descarga las fotos)
                addOption("--yes-playlist")
                addOption("--playlist-items", "1:$MAX_CARRUSEL")
            } else {
                // YouTube y demás: solo el vídeo del enlace, aunque sea de una lista
                addOption("--no-playlist")
            }
            addOption("--no-mtime")
            // video.mp4 si es uno solo; video-1.mp4, video-2.mp4… si es un carrusel
            addOption("-o", File(carpeta, "video%(playlist_index&-{}|)s.%(ext)s").absolutePath)
            // yt-dlp las lee y, al terminar, guarda en el mismo archivo las que se renueven
            cookies?.let { addOption("--cookies", it.absolutePath) }
            // Guarda el título al terminar (after_move no activa el modo simulación)
            addCommands(
                listOf("--print-to-file", "after_move:%(title)s", File(carpeta, ARCHIVO_TITULO).absolutePath)
            )
        }
        YoutubeDL.getInstance().execute(peticion, id) { progreso, eta, _ ->
            alProgreso((progreso / 100f).coerceIn(0f, 1f), eta)
        }
    }

    // Orden natural: video-2 antes que video-10
    private fun archivosDescargados(carpeta: File): List<File> =
        carpeta.listFiles { f -> f.extension in EXTENSIONES }
            ?.sortedBy { it.nameWithoutExtension.substringAfter('-', "0").toIntOrNull() ?: 0 }
            ?.takeIf { it.isNotEmpty() }
            ?: error(context.getString(R.string.descarga_sin_archivo))

    // ¿Tiene el archivo alguna pista de audio? (se lee el propio MP4, sin red)
    private fun tieneAudio(archivo: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(archivo.absolutePath)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (e: Exception) {
            // Si no se puede leer, no se reintenta a ciegas
            true
        } finally {
            extractor.release()
        }
    }

    private fun esTikTok(url: String): Boolean =
        url.toUri().host?.lowercase()?.let { it == "tiktok.com" || it.endsWith(".tiktok.com") } == true

    // Un título por línea (uno por vídeo si es un carrusel)
    fun leerTitulos(carpeta: File): List<String> =
        File(carpeta, ARCHIVO_TITULO).takeIf { it.exists() }?.readLines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun cancelar(id: String) {
        YoutubeDL.getInstance().destroyProcessById(id)
    }

    // Descarga la última versión estable de yt-dlp desde GitHub
    suspend fun actualizar(): ResultadoActualizacion = withContext(Dispatchers.IO) {
        if (!esperarListo()) return@withContext ResultadoActualizacion.ERROR
        try {
            val resultado = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            _estado.value = EstadoMotor.Listo(YoutubeDL.getInstance().version(context))
            if (resultado == YoutubeDL.UpdateStatus.DONE) {
                ResultadoActualizacion.ACTUALIZADO
            } else {
                ResultadoActualizacion.YA_AL_DIA
            }
        } catch (e: Exception) {
            ResultadoActualizacion.ERROR
        }
    }

    companion object {
        // Archivos finales posibles: vídeo MP4 o solo audio M4A
        val EXTENSIONES = setOf("mp4", "m4a")

        // Máximo de vídeos que se bajan de un carrusel
        private const val MAX_CARRUSEL = 20

        /**
         * Mejor calidad en MP4, prefiriendo H.264 (se comparte y edita sin problemas); si no
         * existe, cualquier MP4 y, en último caso, lo mejor disponible. Con [alturaMax] se
         * limita la resolución; si nada cumple el límite, se baja lo que haya.
         * H.264 aparece como "avc1…" en casi todas las webs y como "h264" en TikTok.
         *
         * TikTok es distinto: su formato de "audio" no es el sonido del vídeo sino la canción
         * de la publicación, y algunos vídeos H.265 vienen sin sonido. Por eso ahí solo se
         * eligen formatos que ya traen imagen y sonido juntos ("b"), nunca vídeo + audio aparte.
         */
        fun formato(alturaMax: Int?, tiktok: Boolean): String {
            val limite = alturaMax?.let { "[height<=$it]" }.orEmpty()
            val opciones = if (tiktok) {
                listOf("b[vcodec^=h264]$limite", "b[vcodec^=avc]$limite", "b$limite", "b[vcodec^=h264]", "b")
            } else {
                listOf(
                    "bv*[ext=mp4][vcodec^=avc]$limite+ba[ext=m4a]",
                    "bv*[ext=mp4][vcodec^=h264]$limite+ba[ext=m4a]",
                    "bv*[ext=mp4]$limite+ba[ext=m4a]",
                    "b[ext=mp4]$limite",
                    "bv*$limite+ba",
                    "b$limite",
                    "b",
                )
            }
            return opciones.distinct().joinToString("/")
        }

        // Segundo intento cuando el vídeo llega sin sonido: imagen y sonido en un solo formato,
        // H.264 primero (TikTok, Instagram y X siempre lo ofrecen con audio)
        fun formatoConAudio(alturaMax: Int?): String {
            val limite = alturaMax?.let { "[height<=$it]" }.orEmpty()
            return listOf(
                "b[vcodec^=h264]$limite", "b[vcodec^=avc]$limite", "b[vcodec^=h264]", "b[vcodec^=avc]", "b",
            ).distinct().joinToString("/")
        }
        private const val ARCHIVO_TITULO = "titulo.txt"

        // Extrae la línea "ERROR: ..." de la salida de yt-dlp para mostrarla al usuario
        fun mensajeDeError(e: Throwable): String {
            val texto = e.message.orEmpty()
            val linea = texto.lineSequence().lastOrNull { it.startsWith("ERROR:") }
            return (linea?.removePrefix("ERROR:") ?: texto.lineSequence().firstOrNull())
                ?.trim()
                ?.ifBlank { null }
                ?: e.javaClass.simpleName
        }
    }
}
