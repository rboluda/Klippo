package es.rboluda.klippo.descarga

import java.io.Serializable

/**
 * Datos con los que se etiqueta un audio que viene de otra fuente (una lista de Spotify buscada
 * en YouTube): en vez de los del vídeo de YouTube, el título, intérprete, álbum, número de pista
 * y la portada de Spotify. También agrupa las canciones de la lista para tratarlas en bloque.
 */
data class EtiquetasAudio(
    // "Artista - Título": nombre en la lista de Klippo y del archivo al guardarlo
    val nombre: String,
    val titulo: String,
    val artista: String?,
    val album: String?,
    val pista: Int?,
    // Ruta local de la portada (JPG) que se incrusta y sirve de miniatura
    val portada: String?,
    val grupo: String?,
    val lista: String?,
    // Orden en la lista de Klippo: las canciones de una lista quedan en su orden original
    val creado: Long,
    // Otros vídeos de YouTube que valen, en orden, por si el primero no se puede bajar
    val alternativas: List<String> = emptyList(),
) : Serializable
