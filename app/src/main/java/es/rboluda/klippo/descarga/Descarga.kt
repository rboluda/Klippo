package es.rboluda.klippo.descarga

import es.rboluda.klippo.ajustes.Calidad
import java.io.File

sealed interface EstadoDescarga {
    data object Descargando : EstadoDescarga
    data object Completada : EstadoDescarga
    data object Cancelada : EstadoDescarga
    data class Fallida(val mensaje: String) : EstadoDescarga
}

// Una descarga: vive en su propia carpeta dentro de cacheDir/descargas/<id>/
data class Descarga(
    val id: String,
    val url: String,
    val carpeta: File,
    // Elegida por el usuario para este vídeo; se reutiliza al reintentar
    val calidad: Calidad = Calidad.MEJOR,
    // Momento de inicio (ms desde epoch); ordena la lista
    val creado: Long = System.currentTimeMillis(),
    val titulo: String? = null,
    val estado: EstadoDescarga = EstadoDescarga.Descargando,
    // De 0 a 1; negativo mientras aún no se sabe (analizando el enlace)
    val progreso: Float = -1f,
    val etaSegundos: Long = -1,
    val archivo: File? = null,
    val miniatura: File? = null,
    // Lista de reproducción o carrusel: elemento que se está bajando y total (0 = no es lista)
    val elemento: Int = 0,
    val totalElementos: Int = 0,
    // Los vídeos de una misma lista comparten [grupo] (el id de la descarga original) y el
    // nombre de la lista; null si la descarga es de un solo vídeo
    val grupo: String? = null,
    val lista: String? = null,
    // Esperando turno: solo se bajan unas pocas a la vez (p. ej. al pedir una lista entera)
    val enCola: Boolean = false,
) {
    // Descarga de solo audio (M4A o MP3): sin editor, se guarda en Music en vez de en Movies
    val esAudio: Boolean get() = archivo?.let(MotorDescarga::esAudio) == true
}
