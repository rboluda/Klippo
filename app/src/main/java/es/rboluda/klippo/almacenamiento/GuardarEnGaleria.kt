package es.rboluda.klippo.almacenamiento

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Copia un archivo de la caché a la galería vía MediaStore (no necesita permisos):
// los vídeos van a Movies/Klippo y los audios (M4A) a Music/Klippo
object GuardarEnGaleria {

    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    // Caracteres que no admiten los nombres de archivo y carpeta
    private val NO_VALIDOS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val ESPACIOS = Regex("""\s+""")

    /**
     * Guarda [archivo] en la galería. Sin más datos queda como klippo_<fecha>.mp4 en la carpeta
     * Klippo; con [subcarpeta] (el nombre de una lista) va dentro de Klippo/<subcarpeta> y con
     * [titulo] el archivo se llama como el vídeo. Si ya existe uno igual, Android le añade (1).
     */
    suspend fun guardar(
        context: Context,
        archivo: File,
        subcarpeta: String? = null,
        titulo: String? = null,
    ): Uri = withContext(Dispatchers.IO) {
        val audio = archivo.extension == "m4a"
        val base = titulo?.let(::nombreValido) ?: "klippo_${LocalDateTime.now().format(FORMATO_FECHA)}"
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

    // Quita lo que no puede ir en un nombre de archivo y lo acorta; null si no queda nada
    fun nombreValido(texto: String): String? =
        texto.replace(NO_VALIDOS, " ").replace(ESPACIOS, " ").trim().trim('.').take(100).trim()
            .ifEmpty { null }
}
