package es.rboluda.klippo.sesion

import androidx.core.net.toUri

// Plataformas que admiten iniciar sesión para descargar contenido que lo exige
enum class Plataforma(
    val nombre: String,
    val urlLogin: String,
    // URL de la que se leen las cookies del WebView
    val urlCookies: String,
    val dominios: List<String>,
    // Cookie que solo existe con la sesión iniciada
    val cookieSesion: String,
    val archivo: String,
) {
    INSTAGRAM(
        nombre = "Instagram",
        urlLogin = "https://www.instagram.com/accounts/login/",
        urlCookies = "https://www.instagram.com",
        dominios = listOf("instagram.com"),
        cookieSesion = "sessionid",
        archivo = "instagram.txt",
    ),
    X(
        nombre = "X",
        urlLogin = "https://x.com/i/flow/login",
        urlCookies = "https://x.com",
        dominios = listOf("x.com", "twitter.com"),
        cookieSesion = "auth_token",
        archivo = "x.txt",
    ),
    FACEBOOK(
        nombre = "Facebook",
        urlLogin = "https://www.facebook.com/login/",
        urlCookies = "https://www.facebook.com",
        // fb.watch y fb.com redirigen a facebook.com, que es donde se usan las cookies
        dominios = listOf("facebook.com", "fb.watch", "fb.com"),
        cookieSesion = "c_user",
        archivo = "facebook.txt",
    );

    // Instagram y X agrupan varios vídeos en una publicación (carrusel)
    val tieneCarruseles: Boolean get() = this == INSTAGRAM || this == X

    companion object {
        fun deUrl(url: String): Plataforma? {
            val host = url.trim().toUri().host?.lowercase() ?: return null
            return entries.firstOrNull { p -> p.dominios.any { host == it || host.endsWith(".$it") } }
        }
    }
}
