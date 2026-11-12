package es.rboluda.klippo.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Una canción tal como la da Spotify
data class PistaSpotify(
    val id: String,
    // Posición en la lista o el álbum (desde 1)
    val numero: Int,
    val titulo: String,
    val artistas: List<String>,
    val duracionMs: Long,
) {
    val artistaPrincipal: String? get() = artistas.firstOrNull()

    // "Artista - Título": lo que se busca en YouTube y el nombre del archivo
    val nombreCompleto: String
        get() = listOfNotNull(artistas.joinToString(", ").ifBlank { null }, titulo).joinToString(" - ")
}

data class ListaSpotify(
    val enlace: EnlaceSpotify,
    val nombre: String,
    // Autor de la lista o artista del álbum
    val autor: String?,
    val portadaUrl: String?,
    val pistas: List<PistaSpotify>,
    // Canciones que tiene de verdad, si Spotify ha devuelto menos (límite de 100 sin cuenta)
    val totalReal: Int?,
) {
    val truncada: Boolean get() = totalReal != null && totalReal > pistas.size
}

class SpotifyException(mensaje: String) : Exception(mensaje)

/**
 * Lee una lista, álbum o canción de Spotify SIN cuenta ni clave de API: la página incrustable
 * (open.spotify.com/embed/…) trae los datos en un JSON (__NEXT_DATA__). Solo se conecta cuando
 * el usuario pega o comparte un enlace de Spotify.
 */
object LectorSpotify {

    // Spotify sin cuenta da como mucho estas canciones de una lista
    const val LIMITE_EMBED = 100

    private const val AGENTE =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    private val DATOS = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    private val TOTAL = Regex("""<meta name="music:song_count" content="(\d+)"""")

    suspend fun leer(texto: String): ListaSpotify = withContext(Dispatchers.IO) {
        val corto = if (EnlacesSpotify.esCorto(texto)) resolverCorto(texto.trim()) else null
        val enlace = EnlacesSpotify.analizar(texto) ?: corto
            ?: throw SpotifyException("Enlace de Spotify no reconocido")
        val html = descargarTexto(enlace.urlEmbed)
        val json = DATOS.find(html)?.groupValues?.get(1)
            ?: throw SpotifyException("Spotify no ha devuelto la lista")
        val entidad = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            ?: throw SpotifyException("Lista no disponible (¿es privada?)")
        val lista = convertir(enlace, entidad)
        // Si llegan justo 100, puede que haya más: la página normal dice cuántas tiene
        if (enlace.tipo != TipoSpotify.TRACK && lista.pistas.size >= LIMITE_EMBED) {
            val total = runCatching {
                TOTAL.find(descargarTexto(enlace.urlPagina))?.groupValues?.get(1)?.toInt()
            }.getOrNull()
            lista.copy(totalReal = total?.takeIf { it > lista.pistas.size })
        } else {
            lista
        }
    }

    // Descarga la portada a [destino] (si aún no está) para mostrarla y ponerla en cada canción
    suspend fun descargarPortada(url: String, destino: File): File? = withContext(Dispatchers.IO) {
        if (destino.exists() && destino.length() > 0) return@withContext destino
        try {
            destino.parentFile?.mkdirs()
            val conexion = abrir(url)
            conexion.inputStream.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
            destino
        } catch (e: Exception) {
            destino.delete()
            null
        }
    }

    private fun convertir(enlace: EnlaceSpotify, e: JSONObject): ListaSpotify {
        val nombre = e.optString("name").ifBlank { e.optString("title") }
        val portada = mayorImagen(e.optJSONObject("coverArt")?.optJSONArray("sources"))
            ?: mayorImagen(e.optJSONObject("visualIdentity")?.optJSONArray("image"))
        val pistas = if (enlace.tipo == TipoSpotify.TRACK) {
            // Una sola canción: sus datos están en la propia entidad
            val artistas = e.optJSONArray("artists").nombres()
            listOf(PistaSpotify(e.optString("id", enlace.id), 1, nombre, artistas, e.optLong("duration")))
        } else {
            val lista = e.optJSONArray("trackList") ?: JSONArray()
            (0 until lista.length()).mapNotNull { i ->
                val p = lista.optJSONObject(i) ?: return@mapNotNull null
                val titulo = p.optString("title").ifBlank { return@mapNotNull null }
                PistaSpotify(
                    id = p.optString("uri").substringAfterLast(':').ifBlank { "$i" },
                    numero = i + 1,
                    titulo = titulo,
                    // "Leftfield, DJUM DJUM" (con espacio duro tras la coma)
                    artistas = p.optString("subtitle").split(',')
                        .map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() },
                    duracionMs = p.optLong("duration"),
                )
            }
        }
        if (pistas.isEmpty()) throw SpotifyException("La lista está vacía")
        val autor = e.optString("subtitle").ifBlank { null }
            ?: pistas.singleOrNull()?.artistas?.joinToString(", ")
        return ListaSpotify(enlace, nombre, autor, portada, pistas, totalReal = null)
    }

    private fun JSONArray?.nombres(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.optString("name")?.ifBlank { null } }

    // La imagen más grande de una lista de tamaños (o la única si no indican tamaño)
    private fun mayorImagen(imagenes: JSONArray?): String? {
        if (imagenes == null || imagenes.length() == 0) return null
        return (0 until imagenes.length()).mapNotNull { imagenes.optJSONObject(it) }
            .maxByOrNull { it.optInt("maxWidth", it.optInt("width", 0)) }
            ?.optString("url")?.ifBlank { null }
    }

    // spotify.link redirige (a veces en varios saltos) a open.spotify.com; si acaba en una
    // página que redirige con JavaScript, el enlace se busca en el propio HTML
    private fun resolverCorto(url: String): EnlaceSpotify? {
        var actual = url
        repeat(5) {
            val conexion = abrir(actual, seguir = false)
            val destino = conexion.getHeaderField("Location")
            if (conexion.responseCode in 300..399 && destino != null) {
                conexion.disconnect()
                actual = URL(URL(actual), destino).toString()
                EnlacesSpotify.analizar(actual)?.let { return it }
            } else {
                val cuerpo = runCatching { conexion.inputStream.bufferedReader().use { it.readText() } }.getOrNull()
                return cuerpo?.let(EnlacesSpotify::buscarEnTexto)
            }
        }
        return null
    }

    private fun descargarTexto(url: String): String {
        val conexion = abrir(url)
        if (conexion.responseCode == 404) throw SpotifyException("Spotify no encuentra la lista (404)")
        if (conexion.responseCode !in 200..299) throw SpotifyException("Spotify ha respondido ${conexion.responseCode}")
        return conexion.inputStream.bufferedReader().use { it.readText() }
    }

    private fun abrir(url: String, seguir: Boolean = true): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = seguir
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", AGENTE)
            setRequestProperty("Accept-Language", "es,en;q=0.8")
        }
}
