package es.rboluda.klippo.util

import androidx.core.net.toUri

// Utilidades para reconocer enlaces de vídeo en texto pegado o compartido
object Enlaces {

    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    private val DOMINIOS_SOPORTADOS = listOf(
        "youtube.com", "youtu.be", "tiktok.com", "x.com", "twitter.com", "instagram.com",
        "facebook.com", "fb.watch", "fb.com",
    )

    // Las apps suelen compartir "Mira este vídeo https://..." : se queda solo la URL
    fun extraerUrl(texto: String?): String? =
        texto?.let { URL.find(it)?.value?.trimEnd('.', ',', ')', ']') }

    // Solo se autorrellena desde el portapapeles si es de una plataforma conocida
    fun esSoportada(url: String): Boolean {
        val host = url.toUri().host?.lowercase() ?: return false
        return DOMINIOS_SOPORTADOS.any { host == it || host.endsWith(".$it") }
    }
}
