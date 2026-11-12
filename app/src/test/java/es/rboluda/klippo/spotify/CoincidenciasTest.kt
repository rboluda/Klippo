package es.rboluda.klippo.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoincidenciasTest {

    private val luces = PistaSpotify("1", 1, "Luces del Puerto (Canción de Noche)", listOf("Robots de Lata"), 228_466)

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
        val r = Coincidencias.evaluar(luces, listOf(candidato("Luces del Puerto (Canción de Noche)", "Robots de Lata - Topic", 229)))
        assertEquals(Similitud.IDENTICA, r.similitud)
    }

    @Test
    fun mismaCancionConDuracionDistintaEsParecida() {
        // El videoclip tiene una intro larga: se llama igual pero dura 40 s más
        val r = Coincidencias.evaluar(luces, listOf(candidato("Robots de Lata - Luces del Puerto (Canción de Noche) (Official Music Video)", "Robots de Lata", 268)))
        assertEquals(Similitud.PARECIDA, r.similitud)
    }

    @Test
    fun prefiereLaDeIgualDuracion() {
        val buena = candidato("Robots de Lata - Luces del Puerto (Canción de Noche)", "Robots de Lata", 228, id = "bueno")
        val larga = candidato("Robots de Lata - Luces del Puerto (Canción de Noche) Official Video", "Robots de Lata", 300, id = "largo")
        val r = Coincidencias.evaluar(luces, listOf(larga, buena))
        assertEquals("bueno", r.candidato?.id)
        assertEquals(Similitud.IDENTICA, r.similitud)
    }

    @Test
    fun directoSePenalizaSalvoQueSpotifyLoDiga() {
        val enVivo = candidato("Robots de Lata - Luces del Puerto (Canción de Noche) (Live)", "Robots de Lata", 229)
        assertEquals(Similitud.PARECIDA, Coincidencias.evaluar(luces, listOf(enVivo)).similitud)

        val lucesLive = luces.copy(titulo = "Luces del Puerto (Canción de Noche) - Live")
        val r = Coincidencias.evaluar(lucesLive, listOf(candidato("Robots de Lata - Luces del Puerto (Canción de Noche) Live", "Robots de Lata", 229)))
        assertEquals(Similitud.IDENTICA, r.similitud)
    }

    @Test
    fun coverYKaraokeNoSonIdenticas() {
        val cover = candidato("Luces del Puerto (Canción de Noche) - Robots de Lata cover", "Banda Cualquiera", 228)
        val karaoke = candidato("Luces del Puerto Canción de Noche Karaoke Robots de Lata", "Karaoke Hits", 228)
        assertEquals(Similitud.PARECIDA, Coincidencias.evaluar(luces, listOf(cover)).similitud)
        assertEquals(Similitud.PARECIDA, Coincidencias.evaluar(luces, listOf(karaoke)).similitud)
    }

    @Test
    fun otraCancionNoSeEncuentra() {
        val r = Coincidencias.evaluar(luces, listOf(candidato("Robots de Lata - Mareas de Cobre", "Robots de Lata", 296)))
        assertEquals(Similitud.NO_ENCONTRADA, r.similitud)
        assertNull(r.candidato)
        assertEquals(Similitud.NO_ENCONTRADA, Coincidencias.evaluar(luces, emptyList()).similitud)
    }

    @Test
    fun ignoraFeatYRemasterDeSpotify() {
        val pista = PistaSpotify("2", 1, "Bajo la Lluvia - Remastered 2011", listOf("Los Tejados", "Nube Lenta"), 248_000)
        assertEquals("Bajo la Lluvia", Coincidencias.tituloBase(pista.titulo))
        val r = Coincidencias.evaluar(pista, listOf(candidato("Los Tejados & Nube Lenta - Bajo la Lluvia (Official Video)", "Los Tejados Official", 247)))
        assertEquals(Similitud.IDENTICA, r.similitud)
        assertEquals("Song", Coincidencias.tituloBase("Song (feat. Otro Artista)"))
    }

    @Test
    fun tildesYMayusculasDaIgual() {
        val pista = PistaSpotify("3", 1, "Canción del Farolero", listOf("La Banda Mínima"), 200_000)
        val r = Coincidencias.evaluar(pista, listOf(candidato("LA BANDA MINIMA - CANCION DEL FAROLERO", "La Banda Mínima - Topic", 201)))
        assertEquals(Similitud.IDENTICA, r.similitud)
    }
}
