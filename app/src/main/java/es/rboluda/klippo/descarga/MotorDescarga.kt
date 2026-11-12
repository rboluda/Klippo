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
import es.rboluda.klippo.edicion.Ffmpeg
import es.rboluda.klippo.sesion.Plataforma
import es.rboluda.klippo.spotify.CandidatoYoutube
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

// Avance de una descarga. [progreso] va de 0 a 1 sobre el total: en una lista o carrusel
// cuenta todos sus elementos; [elemento] es el que se está bajando (de 1 a [total]) y
// [total] es 0 si el enlace es de un solo vídeo.
data class Avance(val progreso: Float, val etaSegundos: Long, val elemento: Int = 0, val total: Int = 0)

// Lo que deja una descarga: los archivos en orden, el título de cada uno y, si era una lista
// de reproducción o un carrusel, su nombre
data class ResultadoDescarga(val archivos: List<File>, val titulos: Map<String, String>, val lista: String?) {
    fun titulo(archivo: File): String? = titulos[archivo.name]
}

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
    // normalmente, varios si es una lista de reproducción o un carrusel de Instagram o X
    // (video-1.mp4, video-2.mp4…).
    // Bloquea hasta terminar; se cancela con cancelar(id), que lanza CanceledException.
    suspend fun descargar(
        id: String,
        url: String,
        carpeta: File,
        // Cookies de sesión (Instagram, X) si el usuario ha iniciado sesión
        cookies: File?,
        calidad: Calidad,
        alProgreso: (Avance) -> Unit,
    ): ResultadoDescarga = withContext(Dispatchers.IO) {
        check(esperarListo()) { context.getString(R.string.motor_no_listo) }
        carpeta.mkdirs()
        val formatoNormal = if (calidad.soloAudio) null else formato(calidad.alturaMax, esTikTok(url))
        ejecutar(id, url, carpeta, cookies, calidad, formatoNormal, alProgreso)
        var archivos = archivosDescargados(carpeta)

        // Comprobación de audio: hay formatos que dicen tener sonido y no lo tienen (algunos
        // de TikTok, yt-dlp issue 16622). Si un vídeo llega mudo:
        // 1) se repite la descarga pidiendo imagen y sonido juntos en H.264 (TikTok cambia la
        //    lista de formatos entre peticiones y a la segunda suele haber uno bueno);
        if (!calidad.soloAudio && archivos.any { !tieneAudio(it) }) {
            archivos.forEach { it.delete() }
            File(carpeta, ARCHIVO_TITULO).delete()
            ejecutar(id, url, carpeta, cookies, calidad, formatoConAudio(calidad.alturaMax), alProgreso)
            archivos = archivosDescargados(carpeta)
        }
        // 2) si sigue mudo, la plataforma ya no ofrece ningún formato completo con sonido
        //    (TikTok lo da aparte, como formato "audio"): se descarga esa pista y se une al vídeo
        if (!calidad.soloAudio && archivos.size == 1 && !tieneAudio(archivos.first())) {
            anadirAudioAparte(id, url, carpeta, cookies, archivos.first(), alProgreso)
        }
        val datos = leerDatos(carpeta)
        ResultadoDescarga(
            archivos = archivos,
            titulos = datos.mapNotNull { (ruta, d) -> d.second?.let { File(ruta).name to it } }.toMap(),
            // El nombre de la lista solo interesa si de verdad hay varios archivos
            lista = if (archivos.size > 1) datos.values.firstNotNullOfOrNull { it.first } else null,
        )
    }

    // Descarga solo el audio del enlace y lo une a [video] (imagen copiada tal cual, audio en AAC
    // cortado a la duración del vídeo). Si algo falla, el vídeo se queda como estaba.
    private suspend fun anadirAudioAparte(
        id: String,
        url: String,
        carpeta: File,
        cookies: File?,
        video: File,
        alProgreso: (Avance) -> Unit,
    ) {
        val temporal = File(carpeta, "audio-aparte").apply { mkdirs() }
        try {
            val peticion = YoutubeDLRequest(url).apply {
                addOption("-f", "ba[ext=m4a]/ba")
                addOption("--no-playlist")
                addOption("-o", File(temporal, "audio.%(ext)s").absolutePath)
                cookies?.let { addOption("--cookies", it.absolutePath) }
            }
            YoutubeDL.getInstance().execute(peticion, id) { progreso, eta, _ ->
                alProgreso(Avance((progreso / 100f).coerceIn(0f, 1f), eta))
            }
            val audio = temporal.listFiles()?.firstOrNull { it.isFile && it.length() > 0 } ?: return
            val unido = File(temporal, "unido.mp4")
            Ffmpeg.ejecutar(
                context,
                listOf(
                    "-i", video.absolutePath, "-i", audio.absolutePath,
                    "-map", "0:v:0", "-map", "1:a:0",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "160k",
                    "-shortest", "-movflags", "+faststart",
                    "-f", "mp4", unido.absolutePath,
                ),
                duracionUs = 0,
                alProgreso = {},
            )
            // renameTo sustituye el archivo de golpe (si fallara, el vídeo original sigue intacto)
            if (tieneAudio(unido)) unido.renameTo(video)
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            // Sin audio aparte disponible: se entrega el vídeo tal cual
        } finally {
            temporal.deleteRecursively()
        }
    }

    // Una ejecución de yt-dlp. [formatoVideo] null = solo audio
    private fun ejecutar(
        id: String,
        url: String,
        carpeta: File,
        cookies: File?,
        calidad: Calidad,
        formatoVideo: String?,
        alProgreso: (Avance) -> Unit,
    ) {
        val peticion = YoutubeDLRequest(url).apply {
            if (formatoVideo == null) {
                if (calidad.formatoAudio == "mp3") {
                    // MP3: se parte del mejor audio (sea cual sea) y FFmpeg lo pasa a MP3 VBR de
                    // la máxima calidad (V0, unos 245 kbps)
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                } else {
                    // M4A (AAC) tal cual si existe; si no, FFmpeg lo convierte
                    addOption("-f", "bestaudio[ext=m4a]/bestaudio")
                    addOption("-x")
                    addOption("--audio-format", "m4a")
                }
                // La imagen del vídeo sirve de miniatura (el audio no tiene fotogramas): se
                // guarda con el mismo nombre que el audio (video.jpg, video-2.jpg…)
                addOption("--write-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                // Esa imagen va también dentro del audio como carátula (con --write-thumbnail
                // yt-dlp conserva el JPG), junto con título y artista. En una lista, su nombre
                // pasa a ser el álbum y la posición, el número de pista (si no es una lista,
                // yt-dlp solo avisa y sigue)
                addOption("--embed-thumbnail")
                addOption("--embed-metadata")
                addCommands(
                    listOf(
                        "--parse-metadata", "playlist_title:%(album)s",
                        "--parse-metadata", "playlist_index:%(track_number)s",
                    )
                )
            } else {
                addOption("-f", formatoVideo)
                // Si solo hay WebM/MKV, se cambia el contenedor a MP4 sin recodificar
                addOption("--remux-video", "mp4")
            }
            if (Plataforma.deUrl(url)?.tieneCarruseles == true) {
                // Instagram y X: un carrusel es una "lista"; se bajan todos sus vídeos
                // (yt-dlp no descarga las fotos)
                addOption("--yes-playlist")
                addOption("--playlist-items", "1:$MAX_CARRUSEL")
            } else {
                // YouTube y demás: solo el vídeo del enlace, aunque sea de una lista. Si el
                // enlace es directamente de una lista, se baja entera (hasta MAX_LISTA)
                addOption("--no-playlist")
                addOption("--playlist-items", "1:$MAX_LISTA")
            }
            // En una lista, un vídeo privado o borrado no debe parar los demás
            addOption("--ignore-errors")
            addOption("--no-mtime")
            // video.mp4 si es uno solo; video-1.mp4, video-2.mp4… si es un carrusel
            addOption("-o", File(carpeta, "video%(playlist_index&-{}|)s.%(ext)s").absolutePath)
            // yt-dlp las lee y, al terminar, guarda en el mismo archivo las que se renueven
            cookies?.let { addOption("--cookies", it.absolutePath) }
            // Al terminar cada archivo guarda su ruta, el nombre de la lista (vacío si no es
            // una lista) y el título, separados por tabuladores (after_move no activa el modo
            // simulación)
            addCommands(
                listOf(
                    "--print-to-file",
                    "after_move:%(filepath)s\t%(playlist_title|)s\t%(title)s",
                    File(carpeta, ARCHIVO_TITULO).absolutePath,
                )
            )
        }
        // yt-dlp anuncia cada elemento de una lista con "[download] Downloading item 3 of 10";
        // el porcentaje que da es el del archivo en curso y aquí se pasa a porcentaje total
        var elemento = 0
        var total = 0
        try {
            YoutubeDL.getInstance().execute(peticion, id) { progreso, eta, linea ->
                val nuevo = ELEMENTO_DE_LISTA.find(linea)
                if (nuevo != null) {
                    elemento = nuevo.groupValues[1].toInt()
                    total = nuevo.groupValues[2].toInt()
                }
                val avance = if (total > 1) {
                    // Al empezar un elemento, el porcentaje aún es el del anterior (100 %)
                    val delArchivo = if (nuevo != null) 0f else (progreso / 100f).coerceIn(0f, 1f)
                    Avance(((elemento - 1) + delArchivo) / total, -1, elemento, total)
                } else {
                    Avance((progreso / 100f).coerceIn(0f, 1f), eta)
                }
                alProgreso(avance)
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            // Con --ignore-errors, yt-dlp acaba con error si algún elemento de la lista ha
            // fallado: si se han bajado los demás, se dan por buenos. En solo audio, si lo que
            // falla es poner la carátula, el audio ya está hecho y también vale
            val hayArchivos = carpeta.listFiles { f -> f.extension in EXTENSIONES }?.isNotEmpty() == true
            val aceptable = total > 1 || formatoVideo == null
            if (!aceptable || !hayArchivos) throw e
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

    // Datos que deja yt-dlp de cada archivo: ruta → (nombre de la lista, título)
    private fun leerDatos(carpeta: File): Map<String, Pair<String?, String?>> =
        File(carpeta, ARCHIVO_TITULO).takeIf { it.exists() }?.readLines().orEmpty()
            .mapNotNull { linea ->
                val partes = linea.split('\t', limit = 3)
                if (partes.size < 3) return@mapNotNull null
                partes[0].trim() to (partes[1].trim().ifEmpty { null } to partes[2].trim().ifEmpty { null })
            }
            .toMap()

    /**
     * Sustituye los datos del audio (los del vídeo de YouTube) por [etiquetas] y pone [portada]
     * como carátula, sin recodificar. Si FFmpeg falla, el audio se queda como estaba.
     */
    suspend fun etiquetar(audio: File, etiquetas: EtiquetasAudio, portada: File?): Unit = withContext(Dispatchers.IO) {
        val mp3 = audio.extension == "mp3"
        val salida = File(audio.parentFile, "etiquetado.${audio.extension}")
        val datos = listOfNotNull(
            "title" to etiquetas.titulo,
            etiquetas.artista?.let { "artist" to it },
            etiquetas.album?.let { "album" to it },
            etiquetas.pista?.let { "track" to it.toString() },
        ).flatMap { (clave, valor) -> listOf("-metadata", "$clave=$valor") }
        val argumentos = buildList {
            addAll(listOf("-i", audio.absolutePath))
            portada?.let { addAll(listOf("-i", it.absolutePath)) }
            // Solo el audio del original (sin la imagen de YouTube que ya llevaba)
            addAll(listOf("-map", "0:a:0"))
            if (portada != null) addAll(listOf("-map", "1:0"))
            addAll(listOf("-c", "copy", "-map_metadata", "-1"))
            if (portada != null) {
                addAll(listOf("-disposition:v:0", "attached_pic"))
                if (mp3) addAll(listOf("-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
            }
            if (mp3) addAll(listOf("-id3v2_version", "3"))
            addAll(datos)
            add(salida.absolutePath)
        }
        try {
            Ffmpeg.ejecutar(context, argumentos, duracionUs = 0, alProgreso = {})
            if (salida.length() > 0) salida.renameTo(audio)
        } catch (e: Exception) {
            // Si lo que falla es la portada, al menos se ponen los datos; si no, tal cual
            if (portada != null) etiquetar(audio, etiquetas, null)
        } finally {
            salida.delete()
        }
    }

    /**
     * Busca en YouTube cada texto de [consultas] (los 5 primeros resultados, sin descargar
     * nada) y va llamando a [alResultado] según llegan. Se reparten en unos pocos procesos de
     * yt-dlp con varias búsquedas cada uno: arrancar Python por cada canción sería lentísimo.
     * Las consultas que fallan se devuelven con la lista vacía.
     */
    suspend fun buscarEnYoutube(
        idProceso: String,
        consultas: List<String>,
        alResultado: (consulta: String, candidatos: List<CandidatoYoutube>) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        check(esperarListo()) { context.getString(R.string.motor_no_listo) }
        val pendientes = consultas.distinct()
        val tamano = ((pendientes.size + PROCESOS_BUSQUEDA - 1) / PROCESOS_BUSQUEDA).coerceAtLeast(1)
        pendientes.chunked(tamano).mapIndexed { i, bloque ->
            async { buscarBloque("$idProceso-$i", bloque, alResultado) }
        }.awaitAll()
    }

    // Detiene una búsqueda en curso (cada proceso lleva el id con su número de bloque)
    fun cancelarBusqueda(idProceso: String) {
        repeat(PROCESOS_BUSQUEDA) { YoutubeDL.getInstance().destroyProcessById("$idProceso-$it") }
    }

    private fun buscarBloque(
        id: String,
        consultas: List<String>,
        alResultado: (String, List<CandidatoYoutube>) -> Unit,
    ) {
        val respondidas = mutableSetOf<String>()
        val peticion = YoutubeDLRequest(consultas.map { "ytsearch$RESULTADOS_BUSQUEDA:$it" }).apply {
            // Una línea JSON por búsqueda, con los datos básicos de cada vídeo (sin abrirlos)
            addOption("--dump-single-json")
            addOption("--flat-playlist")
            addOption("--ignore-errors")
            addOption("--no-warnings")
        }
        try {
            YoutubeDL.getInstance().execute(peticion, id) { _, _, linea ->
                if (!linea.startsWith("{")) return@execute
                val json = runCatching { JSONObject(linea) }.getOrNull() ?: return@execute
                // El "id" de la búsqueda es el propio texto buscado
                val consulta = json.optString("id").takeIf { it in consultas } ?: return@execute
                val entradas = json.optJSONArray("entries")
                val candidatos = (0 until (entradas?.length() ?: 0)).mapNotNull { j ->
                    val e = entradas?.optJSONObject(j) ?: return@mapNotNull null
                    // Los directos en curso no tienen audio descargable
                    if (e.optString("live_status") == "is_live") return@mapNotNull null
                    CandidatoYoutube(
                        id = e.optString("id").ifBlank { return@mapNotNull null },
                        titulo = e.optString("title"),
                        canal = e.optString("channel").ifBlank { e.optString("uploader") }.ifBlank { null },
                        duracionSeg = e.optDouble("duration").takeUnless { it.isNaN() }?.toInt(),
                    )
                }
                respondidas += consulta
                alResultado(consulta, candidatos)
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            // Alguna búsqueda ha fallado: las que no han llegado se dan como sin resultados
        }
        consultas.filterNot { it in respondidas }.forEach { alResultado(it, emptyList()) }
    }

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
        // Archivos finales posibles: vídeo MP4 o solo audio M4A o MP3
        val EXTENSIONES_AUDIO = setOf("m4a", "mp3")
        val EXTENSIONES = setOf("mp4") + EXTENSIONES_AUDIO

        fun esAudio(archivo: File): Boolean = archivo.extension in EXTENSIONES_AUDIO

        // Máximo de vídeos que se bajan de un carrusel y de una lista de reproducción
        private const val MAX_CARRUSEL = 20
        private const val MAX_LISTA = 100

        // Búsquedas en YouTube: resultados por canción y procesos de yt-dlp en paralelo
        private const val RESULTADOS_BUSQUEDA = 5
        private const val PROCESOS_BUSQUEDA = 3

        private val ELEMENTO_DE_LISTA = Regex("""Downloading (?:item|video) (\d+) of (\d+)""")

        // Miniatura que yt-dlp guarda junto a un audio (video-2.m4a → video-2.jpg)
        fun miniaturaDescargada(archivo: File): File =
            File(archivo.parentFile, archivo.nameWithoutExtension + ".jpg")

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
            // Los archivos completos de Facebook ("sd"/"hd") no dicen su resolución: con "<=?"
            // no se descartan por no saberla
            val limiteCompleto = alturaMax?.let { "[height<=?$it]" }.orEmpty()
            val opciones = if (tiktok) {
                listOf("b[vcodec^=h264]$limite", "b[vcodec^=avc]$limite", "b$limite", "b[vcodec^=h264]", "b")
            } else {
                listOf(
                    // 1. H.264 con el audio aparte (YouTube, Instagram, X)
                    "bv*[ext=mp4][vcodec^=avc]$limite+ba[ext=m4a]",
                    "bv*[ext=mp4][vcodec^=h264]$limite+ba[ext=m4a]",
                    // 2. Un MP4 completo con imagen y sonido (el "hd" de Facebook es H.264)
                    "b[ext=mp4][vcodec^=avc]$limiteCompleto",
                    "b[ext=mp4][vcodec^=h264]$limiteCompleto",
                    "b[ext=mp4]$limiteCompleto",
                    // 3. Otros códecs (VP9, AV1) con el audio aparte, remuxados a MP4
                    "bv*[ext=mp4]$limite+ba[ext=m4a]",
                    "bv*$limite+ba",
                    "b$limiteCompleto",
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
