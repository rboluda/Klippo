package es.rboluda.klippo.almacenamiento

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import es.rboluda.klippo.descarga.MotorDescarga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Copia un archivo de la caché a la galería vía MediaStore (no necesita permisos):
// los vídeos van a Movies/Klippo y los audios (M4A o MP3) a Music/Klippo
object GuardarEnGaleria {

    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    // Caracteres que no admiten los nombres de archivo y carpeta
    private val NO_VALIDOS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val ESPACIOS = Regex("""\s+""")

    /**
     * Guarda [archivo] en la galería. Sin más datos queda como klippo_<fecha>.mp4 en la carpeta
     * Klippo; con [subcarpeta] (el nombre de una lista) va dentro de Klippo/<subcarpeta> y con
     * [titulo] el archivo se llama como el vídeo (si no se indica, se busca en el meta.json de la
     * descarga). Con [pista] (de una lista de [totalPistas]) el número va delante para que la
     * carpeta quede en el orden de la lista: "06 - Artista - Título". Si ya existe uno igual,
     * Android le añade (1).
     */
    suspend fun guardar(
        context: Context,
        archivo: File,
        subcarpeta: String? = null,
        titulo: String? = null,
        pista: Int? = null,
        totalPistas: Int? = null,
    ): Uri = withContext(Dispatchers.IO) {
        val audio = MotorDescarga.esAudio(archivo)
        val titulado = (titulo ?: MetaDescarga.leerTitulo(archivo.parentFile))?.let(::nombreValido)
        val base = titulado?.let { prefijoPista(pista, totalPistas) + it }
            ?: "klippo_${LocalDateTime.now().format(FORMATO_FECHA)}"
        val nombre = "$base.${archivo.extension}"
        val carpeta = "Klippo" + (subcarpeta?.let(::nombreValido)?.let { "/$it" } ?: "")
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
            put(MediaStore.MediaColumns.MIME_TYPE, Compartir.tipo(archivo))
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/" + carpeta,
            )
            // Oculto para la galería hasta terminar de copiar
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val coleccion = if (audio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = resolver.insert(coleccion, valores) ?: error("No se pudo crear el archivo en la galería")
        try {
            resolver.openOutputStream(uri)?.use { salida ->
                archivo.inputStream().use { it.copyTo(salida) }
            } ?: error("No se pudo escribir en la galería")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    // "06 - " (al menos dos cifras; tres si la lista llega a 100); vacío si no es de una lista
    fun prefijoPista(pista: Int?, totalPistas: Int?): String {
        if (pista == null || pista <= 0) return ""
        val cifras = maxOf(2, maxOf(pista, totalPistas ?: 0).toString().length)
        return pista.toString().padStart(cifras, '0') + " - "
    }

    // Quita lo que no puede ir en un nombre de archivo y lo acorta; null si no queda nada
    fun nombreValido(texto: String): String? =
        texto.replace(NO_VALIDOS, " ").replace(ESPACIOS, " ").trim().trim('.').take(100).trim()
            .ifEmpty { null }
}
