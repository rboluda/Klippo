package es.rboluda.klippo.spotify

import java.text.Normalizer
import kotlin.math.abs

// Lo parecido que es el resultado de YouTube a la canción de Spotify
// MANUAL: el usuario lo ha elegido él (buscando a mano o pegando un enlace)
enum class Similitud { IDENTICA, PARECIDA, NO_ENCONTRADA, MANUAL }

// Un resultado de la búsqueda en YouTube
data class CandidatoYoutube(
    val id: String,
    val titulo: String,
    val canal: String?,
    val duracionSeg: Int?,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

// [alternativas]: los demás resultados que también valen (de mejor a peor), por si el elegido
// no se puede bajar (p. ej. un vídeo con restricción de edad)
data class Coincidencia(
    val similitud: Similitud,
    val candidato: CandidatoYoutube?,
    val alternativas: List<CandidatoYoutube> = emptyList(),
)

/**
 * Elige, entre los resultados de YouTube, el que más se parece a una canción de Spotify y dice
 * cuánto se parece. Se mira el título, el intérprete (en el título o en el canal), la duración
 * y si es otra versión (directo, cover, karaoke…). Sin Android: se prueba con JUnit.
 */
object Coincidencias {

    // Versiones que no son la grabación original: se penalizan salvo que Spotify las nombre
    private val VERSIONES = listOf(
        "live", "directo", "en vivo", "cover", "karaoke", "reaction", "reaccion", "remix",
        "instrumental", "sped up", "slowed", "nightcore", "8d", "tutorial", "lesson", "acoustic",
        "acustico", "piano version", "bass boosted", "tribute",
    )

    // Palabras de relleno de los títulos de YouTube que no dicen nada de la canción
    private val RELLENO = setOf(
        "official", "oficial", "video", "videoclip", "music", "audio", "lyrics", "lyric", "letra",
        "visualizer", "hd", "hq", "4k", "remastered", "remaster", "remasterizado", "remasterizada",
        "clip", "full", "version", "mv", "topic", "vevo", "feat", "ft", "featuring", "with", "con",
        "the", "el", "la", "los", "las", "a", "y", "and", "of", "de", "del",
    )

    // Partes del título de Spotify que YouTube no suele llevar: "(feat. X)", "- Remastered 2009"…
    private val SUFIJOS_SPOTIFY = listOf(
        Regex("""\s*[(\[](?:feat\.?|ft\.?|with|con)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s*[(\[][^)\]]*remaster[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s+-\s+[^-]*remaster.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s+-\s+(?:mono|stereo|single|radio edit|album version)\b.*$""", RegexOption.IGNORE_CASE),
    )

    // Diferencia de duración (en segundos) que se considera la misma grabación
    const val TOLERANCIA_IDENTICA = 5
    // A partir de esta diferencia, la duración no aporta nada
    private const val DIFERENCIA_MAXIMA = 30

    // Título de Spotify sin "(feat. …)" ni "- Remastered": lo que se busca y se compara
    fun tituloBase(titulo: String): String =
        SUFIJOS_SPOTIFY.fold(titulo) { t, r -> t.replace(r, "") }.trim().ifEmpty { titulo }

    // Minúsculas, sin tildes y solo letras y números separados por un espacio
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun palabras(texto: String): List<String> =
        normalizar(texto).split(' ').filter { it.isNotEmpty() }

    /**
     * Puntuación de la duración, de 0 a 1: 1 si difiere como mucho [TOLERANCIA_IDENTICA]
     * segundos, bajando hasta 0 a los [DIFERENCIA_MAXIMA]. Sin duración en YouTube, 0,5.
     */
    fun puntuarDuracion(duracionMs: Long, duracionYoutubeSeg: Int?): Double {
        if (duracionYoutubeSeg == null || duracionMs <= 0) return 0.5
        val diferencia = abs(duracionMs / 1000.0 - duracionYoutubeSeg)
        return when {
            diferencia <= TOLERANCIA_IDENTICA -> 1.0
            diferencia >= DIFERENCIA_MAXIMA -> 0.0
            else -> 1.0 - (diferencia - TOLERANCIA_IDENTICA) / (DIFERENCIA_MAXIMA - TOLERANCIA_IDENTICA)
        }
    }

    // ¿Dura lo mismo (con margen)? Si YouTube no da la duración, no se puede asegurar
    fun mismaDuracion(duracionMs: Long, duracionYoutubeSeg: Int?): Boolean =
        duracionYoutubeSeg != null && duracionMs > 0 &&
            abs(duracionMs / 1000.0 - duracionYoutubeSeg) <= TOLERANCIA_IDENTICA

    // Parecido de un solo resultado (para mostrarlo en la búsqueda manual)
    fun similitud(pista: PistaSpotify, candidato: CandidatoYoutube): Similitud = analizar(pista, candidato).similitud

    fun evaluar(pista: PistaSpotify, candidatos: List<CandidatoYoutube>): Coincidencia {
        // Primero la mejor categoría y, dentro de ella, la mejor puntuación
        val ordenados = candidatos.map { it to analizar(pista, it) }
            .filter { it.second.similitud != Similitud.NO_ENCONTRADA }
            .sortedWith(
                compareBy<Pair<CandidatoYoutube, Analisis>>({ it.second.similitud.ordinal }, { -it.second.puntos })
            )
        val mejor = ordenados.firstOrNull() ?: return Coincidencia(Similitud.NO_ENCONTRADA, null)
        return Coincidencia(mejor.second.similitud, mejor.first, ordenados.drop(1).map { it.first })
    }

    private data class Analisis(val similitud: Similitud, val puntos: Double)

    private fun analizar(pista: PistaSpotify, c: CandidatoYoutube): Analisis {
        val tituloSpotify = palabras(tituloBase(pista.titulo)).filter { it !in RELLENO }
            .ifEmpty { palabras(pista.titulo) }
        val tituloYoutube = palabras(c.titulo).toSet()
        // Parte del título de Spotify que aparece en el de YouTube
        val titulo = tituloSpotify.count { it in tituloYoutube }.toDouble() / tituloSpotify.size.coerceAtLeast(1)

        // El intérprete puede ir en el título ("U2 - Pride") o en el canal ("U2 - Topic", "U2VEVO")
        val textoCanal = normalizar(c.canal.orEmpty()).replace(" ", "")
        val textoTitulo = " ${normalizar(c.titulo)} "
        fun aparece(artista: String): Boolean {
            val n = normalizar(artista)
            return n.isNotEmpty() && (" $n " in textoTitulo || n.replace(" ", "") in textoCanal)
        }
        val artistaPrincipal = pista.artistaPrincipal?.let(::aparece) ?: true
        val algunArtista = pista.artistas.isEmpty() || pista.artistas.any(::aparece)

        // Otra versión (directo, cover…) que la canción de Spotify no menciona
        val textoSpotify = " ${normalizar(pista.titulo)} "
        val otraVersion = VERSIONES.any { v -> " $v " in textoTitulo && " $v " !in textoSpotify }

        val duracion = puntuarDuracion(pista.duracionMs, c.duracionSeg)
        val mismaDuracion = mismaDuracion(pista.duracionMs, c.duracionSeg)
        // Los canales "Artista - Topic" publican el audio oficial tal cual
        val oficial = c.canal?.endsWith(" - Topic", ignoreCase = true) == true

        val puntos = titulo * 40 + (if (artistaPrincipal) 25 else if (algunArtista) 15 else 0) +
            duracion * 30 + (if (oficial) 5 else 0) - (if (otraVersion) 40 else 0)

        val similitud = when {
            // Misma canción: título completo, su intérprete, la misma duración y no es otra versión
            titulo >= 1.0 && algunArtista && mismaDuracion && !otraVersion -> Similitud.IDENTICA
            // Se llama igual (o casi), aunque falle el intérprete, la duración o sea otra versión
            titulo >= 0.75 -> Similitud.PARECIDA
            // Medio título, pero del mismo intérprete y con la misma duración
            titulo >= 0.5 && algunArtista && mismaDuracion -> Similitud.PARECIDA
            else -> Similitud.NO_ENCONTRADA
        }
        return Analisis(similitud, puntos)
    }
}
