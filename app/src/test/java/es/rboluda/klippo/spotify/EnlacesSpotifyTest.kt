package es.rboluda.klippo.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnlacesSpotifyTest {

    private val idLista = "ListaDePrueba000000001"
    private val idAlbum = "AlbumDePrueba000000001"

    @Test
    fun listaSinParametros() {
        assertEquals(
            EnlaceSpotify(TipoSpotify.PLAYLIST, idLista),
            EnlacesSpotify.analizar("https://open.spotify.com/playlist/$idLista"),
        )
    }

    @Test
    fun ignoraSiUtmYPi() {
        val enlace = EnlacesSpotify.analizar(
            "https://open.spotify.com/playlist/$idLista?si=abc123&utm_source=copy-link&pi=u-xyz"
        )
        assertEquals(EnlaceSpotify(TipoSpotify.PLAYLIST, idLista), enlace)
    }

    @Test
    fun albumYCancion() {
        assertEquals(TipoSpotify.ALBUM, EnlacesSpotify.analizar("https://open.spotify.com/album/$idAlbum?si=x")?.tipo)
        assertEquals(
            EnlaceSpotify(TipoSpotify.TRACK, "CancionDePrueba0000001"),
            EnlacesSpotify.analizar("https://open.spotify.com/track/CancionDePrueba0000001"),
        )
    }

    @Test
    fun prefijoDeIdiomaYEmbed() {
        assertEquals(idLista, EnlacesSpotify.analizar("https://open.spotify.com/intl-es/playlist/$idLista")?.id)
        assertEquals(idLista, EnlacesSpotify.analizar("https://open.spotify.com/intl-pt-br/playlist/$idLista")?.id)
        assertEquals(idAlbum, EnlacesSpotify.analizar("https://open.spotify.com/embed/album/$idAlbum")?.id)
    }

    @Test
    fun uriDeSpotifyYEspacios() {
        assertEquals(
            EnlaceSpotify(TipoSpotify.PLAYLIST, idLista),
            EnlacesSpotify.analizar("  spotify:playlist:$idLista \n"),
        )
    }

    @Test
    fun urlDeEmbedYPagina() {
        val enlace = EnlaceSpotify(TipoSpotify.ALBUM, idAlbum)
        assertEquals("https://open.spotify.com/embed/album/$idAlbum", enlace.urlEmbed)
        assertEquals("https://open.spotify.com/album/$idAlbum", enlace.urlPagina)
    }

    @Test
    fun enlacesAcortados() {
        assertTrue(EnlacesSpotify.esCorto("https://spotify.link/EnlaceCorto1"))
        assertTrue(EnlacesSpotify.esSpotify("https://spotify.link/EnlaceCorto1?x=1"))
        assertNull(EnlacesSpotify.analizar("https://spotify.link/EnlaceCorto1"))
    }

    @Test
    fun rechazaLoQueNoEsUnaLista() {
        assertNull(EnlacesSpotify.analizar("https://open.spotify.com/artist/ArtistaDePrueba0000001"))
        assertNull(EnlacesSpotify.analizar("https://open.spotify.com/playlist/corto"))
        assertNull(EnlacesSpotify.analizar("https://www.youtube.com/playlist?list=PL123"))
        assertNull(EnlacesSpotify.analizar("https://open.spotify.com.ejemplo.com/playlist/$idLista"))
        assertNull(EnlacesSpotify.analizar(null))
        assertFalse(EnlacesSpotify.esSpotify("https://www.tiktok.com/@a/video/1"))
    }

    @Test
    fun buscaElEnlaceDentroDeUnaPagina() {
        val html = """<a href="https://open.spotify.com/album/$idAlbum?si=1">abrir</a>"""
        assertEquals(EnlaceSpotify(TipoSpotify.ALBUM, idAlbum), EnlacesSpotify.buscarEnTexto(html))
    }
}
