package es.rboluda.klippo.spotify

// Saca el id de vídeo de un enlace de YouTube pegado a mano. Sin Android: se prueba con JUnit.
object EnlacesYoutube {

    // youtube.com/watch?v=…, m./music./www., /shorts/, /embed/, /live/, /v/ y youtu.be/…
    private val PATRONES = listOf(
        Regex("""^https?://(?:www\.|m\.|music\.)?youtube\.com/watch\?(?:.*&)?v=([A-Za-z0-9_-]{11})(?:[&#].*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^https?://(?:www\.|m\.|music\.)?youtube\.com/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})(?:[/?#].*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^https?://youtu\.be/([A-Za-z0-9_-]{11})(?:[/?#].*)?$""", RegexOption.IGNORE_CASE),
    )

    fun idDeVideo(texto: String?): String? {
        val limpio = texto?.trim() ?: return null
        return PATRONES.firstNotNullOfOrNull { it.matchEntire(limpio)?.groupValues?.get(1) }
    }
}
