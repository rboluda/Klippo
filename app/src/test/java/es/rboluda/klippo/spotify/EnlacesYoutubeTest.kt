package es.rboluda.klippo.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnlacesYoutubeTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun formasHabituales() {
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?v=$id&t=42s",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=abcdef",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id?feature=share",
            "  https://youtu.be/$id  ",
        ).forEach { assertEquals(it, id, EnlacesYoutube.idDeVideo(it)) }
    }

    @Test
    fun rechazaLoQueNoEsUnVideo() {
        assertNull(EnlacesYoutube.idDeVideo("https://www.youtube.com/playlist?list=PL123"))
        assertNull(EnlacesYoutube.idDeVideo("https://www.youtube.com/@canal"))
        assertNull(EnlacesYoutube.idDeVideo("https://youtu.be/corto"))
        assertNull(EnlacesYoutube.idDeVideo("https://www.youtube.com.ejemplo.com/watch?v=$id"))
        assertNull(EnlacesYoutube.idDeVideo("https://open.spotify.com/track/24rv66H94QGVqIXHjA3kn6"))
        assertNull(EnlacesYoutube.idDeVideo(""))
        assertNull(EnlacesYoutube.idDeVideo(null))
    }
}
