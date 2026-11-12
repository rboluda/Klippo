package es.rboluda.klippo.spotify

// Qué es lo que apunta un enlace de Spotify
enum class TipoSpotify(val ruta: String) { PLAYLIST("playlist"), ALBUM("album"), TRACK("track") }

data class EnlaceSpotify(val tipo: TipoSpotify, val id: String) {
    // Versión incrustable, que se puede leer sin cuenta ni clave de API
    val urlEmbed: String get() = "https://open.spotify.com/embed/${tipo.ruta}/$id"
    val urlPagina: String get() = "https://open.spotify.com/${tipo.ruta}/$id"
}

// Reconoce enlaces de Spotify. Sin dependencias de Android para poder probarlo con JUnit.
object EnlacesSpotify {

    // open.spotify.com/[intl-es/][embed/]playlist|album|track/<id de 22 caracteres>[?si=…]
    private val WEB = Regex(
        """^https?://open\.spotify\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(?:embed/)?(playlist|album|track)/([A-Za-z0-9]{22})(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )

    // spotify:playlist:<id> (lo que copia la app de escritorio)
    private val URI = Regex("""^spotify:(playlist|album|track):([A-Za-z0-9]{22})$""", RegexOption.IGNORE_CASE)

    // Enlaces acortados que hay que seguir para saber a qué apuntan
    private val CORTO = Regex("""^https?://spotify\.link/[A-Za-z0-9]+(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)

    // Busca un enlace de lista, álbum o canción dentro de un texto (p. ej. una página HTML)
    private val EN_TEXTO = Regex("""open\.spotify\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(playlist|album|track)/([A-Za-z0-9]{22})""")

    fun analizar(texto: String?): EnlaceSpotify? {
        val limpio = texto?.trim() ?: return null
        val m = WEB.matchEntire(limpio) ?: URI.matchEntire(limpio) ?: return null
        return crear(m.groupValues[1], m.groupValues[2])
    }

    fun esCorto(texto: String?): Boolean = texto != null && CORTO.matches(texto.trim())

    // ¿Es un enlace de Spotify que Klippo sabe tratar (directo o acortado)?
    fun esSpotify(texto: String?): Boolean = analizar(texto) != null || esCorto(texto)

    fun buscarEnTexto(texto: String): EnlaceSpotify? =
        EN_TEXTO.find(texto)?.let { crear(it.groupValues[1], it.groupValues[2]) }

    private fun crear(tipo: String, id: String): EnlaceSpotify? =
        TipoSpotify.entries.find { it.ruta == tipo.lowercase() }?.let { EnlaceSpotify(it, id) }
}
