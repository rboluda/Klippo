package es.rboluda.klippo.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoincidenciasTest {

    private val pride = PistaSpotify("1", 1, "Pride (In The Name Of Love)", listOf("U2"), 228_466)

    private fun candidato(titulo: String, canal: String?, segundos: Int?, id: String = titulo.hashCode().toString()) =
        CandidatoYoutube(id, titulo, canal, segundos)

    // --- Duración ---

    @Test
    fun duracionDentroDeLaTolerancia() {
        assertEquals(1.0, Coincidencias.puntuarDuracion(228_466, 228), 0.0001)
        assertEquals(1.0, Coincidencias.puntuarDuracion(228_466, 233), 0.0001)
        assertTrue(Coincidencias.mismaDuracion(228_466, 224))
    }

    @Test
    fun duracionBajaPocoAPocoHastaCero() {
        val a15 = Coincidencias.puntuarDuracion(200_000, 215)
        val a25 = Coincidencias.puntuarDuracion(200_000, 225)
        assertTrue(a15 in 0.0..1.0 && a25 < a15)
        assertEquals(0.0, Coincidencias.puntuarDuracion(200_000, 230), 0.0001)
        assertEquals(0.0, Coincidencias.puntuarDuracion(200_000, 400), 0.0001)
        assertFalse(Coincidencias.mismaDuracion(200_000, 215))
    }

    @Test
    fun sinDuracionEnYoutubeEsNeutra() {
        assertEquals(0.5, Coincidencias.puntuarDuracion(200_000, null), 0.0001)
        assertFalse(Coincidencias.mismaDuracion(200_000, null))
    }

    // --- Clasificación ---

    @Test
    fun audioOficialEsIdentica() {
        val r = Coincidencias.evaluar(pride, listOf(candidato("Pride (In The Name Of Love)", "U2 - Topic", 229)))
        assertEquals(Similitud.IDENTICA, r.similitud)
    }

    @Test
    fun mismaCancionConDuracionDistintaEsParecida() {
        // El videoclip tiene una intro larga: se llama igual pero dura 40 s más
        val r = Coincidencias.evaluar(pride, listOf(candidato("U2 - Pride (In The Name Of Love) (Official Music Video)", "U2", 268)))
        assertEquals(Similitud.PARECIDA, r.similitud)
    }

    @Test
    fun prefiereLaDeIgualDuracion() {
        val buena = candidato("U2 - Pride (In The Name Of Love)", "U2", 228, id = "bueno")
        val larga = candidato("U2 - Pride (In The Name Of Love) Official Video", "U2", 300, id = "largo")
        val r = Coincidencias.evaluar(pride, listOf(larga, buena))
        assertEquals("bueno", r.candidato?.id)
        assertEquals(Similitud.IDENTICA, r.similitud)
    }

    @Test
    fun directoSePenalizaSalvoQueSpotifyLoDiga() {
        val enVivo = candidato("U2 - Pride (In The Name Of Love) (Live)", "U2", 229)
        assertEquals(Similitud.PARECIDA, Coincidencias.evaluar(pride, listOf(enVivo)).similitud)

        val prideLive = pride.copy(titulo = "Pride (In The Name Of Love) - Live")
        val r = Coincidencias.evaluar(prideLive, listOf(candidato("U2 - Pride (In The Name Of Love) Live", "U2", 229)))
        assertEquals(Similitud.IDENTICA, r.similitud)
    }

    @Test
    fun coverYKaraokeNoSonIdenticas() {
        val cover = candidato("Pride (In The Name Of Love) - U2 cover", "Banda Cualquiera", 228)
        val karaoke = candidato("Pride In The Name Of Love Karaoke U2", "Karaoke Hits", 228)
        assertEquals(Similitud.PARECIDA, Coincidencias.evaluar(pride, listOf(cover)).similitud)
        assertEquals(Similitud.PARECIDA, Coincidencias.evaluar(pride, listOf(karaoke)).similitud)
    }

    @Test
    fun otraCancionNoSeEncuentra() {
        val r = Coincidencias.evaluar(pride, listOf(candidato("U2 - With Or Without You", "U2", 296)))
        assertEquals(Similitud.NO_ENCONTRADA, r.similitud)
        assertNull(r.candidato)
        assertEquals(Similitud.NO_ENCONTRADA, Coincidencias.evaluar(pride, emptyList()).similitud)
    }

    @Test
    fun ignoraFeatYRemasterDeSpotify() {
        val pista = PistaSpotify("2", 1, "Under Pressure - Remastered 2011", listOf("Queen", "David Bowie"), 248_000)
        assertEquals("Under Pressure", Coincidencias.tituloBase(pista.titulo))
        val r = Coincidencias.evaluar(pista, listOf(candidato("Queen & David Bowie - Under Pressure (Official Video)", "Queen Official", 247)))
        assertEquals(Similitud.IDENTICA, r.similitud)
        assertEquals("Song", Coincidencias.tituloBase("Song (feat. Otro Artista)"))
    }

    @Test
    fun tildesYMayusculasDaIgual() {
        val pista = PistaSpotify("3", 1, "Canción del Mariachi", listOf("Los Lobos"), 200_000)
        val r = Coincidencias.evaluar(pista, listOf(candidato("LOS LOBOS - CANCION DEL MARIACHI", "Los Lobos - Topic", 201)))
        assertEquals(Similitud.IDENTICA, r.similitud)
    }
}
