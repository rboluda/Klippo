package es.rboluda.klippo.ui.exportar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.rboluda.klippo.KlippoApp
import es.rboluda.klippo.R
import es.rboluda.klippo.almacenamiento.GuardarEnGaleria
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.edicion.Exportacion
import es.rboluda.klippo.edicion.ParametrosEdicion
import es.rboluda.klippo.ui.Rutas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Datos del vídeo leídos del propio archivo (sin red)
data class InfoVideo(
    val duracionMs: Long,
    val ancho: Int,
    val alto: Int,
    val bytes: Long,
    val miniatura: Bitmap?,
)

sealed interface EstadoExportacion {
    // FFmpeg trabajando; progreso de 0 a 1
    data class Generando(val progreso: Float) : EstadoExportacion
    data class Lista(val archivo: File) : EstadoExportacion
    data class Error(val mensaje: String) : EstadoExportacion
    data object NoDisponible : EstadoExportacion
}

class ExportarViewModel(
    private val app: KlippoApp,
    estado: SavedStateHandle,
    private val parametros: ParametrosEdicion,
) : ViewModel() {

    private val id: String = checkNotNull(estado[Rutas.ARG_ID])

    val descarga: Descarga? = app.descargas.buscar(id)

    private val _estado = MutableStateFlow<EstadoExportacion>(EstadoExportacion.Generando(0f))
    val estadoExportacion: StateFlow<EstadoExportacion> = _estado.asStateFlow()

    private val _info = MutableStateFlow<InfoVideo?>(null)
    val info: StateFlow<InfoVideo?> = _info.asStateFlow()

    private val _guardando = MutableStateFlow(false)
    val guardando: StateFlow<Boolean> = _guardando.asStateFlow()

    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos = _avisos.receiveAsFlow()

    private var trabajo: Job? = null

    init {
        generar()
    }

    // Sin cambios se usa el vídeo descargado tal cual; con cambios, FFmpeg genera uno nuevo.
    // Si se sale de la pantalla, viewModelScope cancela la exportación y mata FFmpeg.
    fun generar() {
        val original = descarga?.archivo?.takeIf { it.exists() }
        if (original == null) {
            _estado.value = EstadoExportacion.NoDisponible
            return
        }
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            _estado.value = EstadoExportacion.Generando(0f)
            val archivo = if (!parametros.hayCambios) {
                original
            } else {
                try {
                    // FFmpeg se extrae al arrancar el motor: hay que esperar a que esté listo
                    check(app.motor.esperarListo()) { app.getString(R.string.motor_error) }
                    Exportacion.exportar(app, id, original, parametros) { progreso ->
                        _estado.value = EstadoExportacion.Generando(progreso)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _estado.value = EstadoExportacion.Error(e.message ?: e.javaClass.simpleName)
                    return@launch
                }
            }
            _info.value = leerInfo(archivo)
            _estado.value = EstadoExportacion.Lista(archivo)
        }
    }

    // Solo Android 13: en 14+ se guarda desde la acción del share sheet
    fun guardarEnGaleria() {
        val archivo = (_estado.value as? EstadoExportacion.Lista)?.archivo ?: return
        if (_guardando.value) return
        _guardando.value = true
        viewModelScope.launch {
            val texto = try {
                GuardarEnGaleria.guardar(app, archivo, titulo = descarga?.titulo)
                app.getString(R.string.galeria_guardado)
            } catch (e: Exception) {
                app.getString(R.string.galeria_error)
            }
            _guardando.value = false
            _avisos.send(texto)
        }
    }

    private suspend fun leerInfo(archivo: File): InfoVideo? = withContext(Dispatchers.IO) {
        val lector = MediaMetadataRetriever()
        try {
            lector.setDataSource(archivo.absolutePath)
            fun entero(clave: Int) = lector.extractMetadata(clave)?.toLongOrNull() ?: 0L
            var ancho = entero(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            var alto = entero(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            // Los vídeos grabados en vertical a veces vienen girados por metadatos
            val rotacion = entero(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
            if (rotacion == 90 || rotacion == 270) ancho = alto.also { alto = ancho }
            InfoVideo(
                duracionMs = entero(MediaMetadataRetriever.METADATA_KEY_DURATION),
                ancho = ancho,
                alto = alto,
                bytes = archivo.length(),
                // Un audio no tiene fotogramas: se usa la imagen descargada del vídeo
                miniatura = lector.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: descarga?.miniatura?.let { BitmapFactory.decodeFile(it.absolutePath) },
            )
        } catch (e: Exception) {
            null
        } finally {
            lector.release()
        }
    }

    companion object {
        fun factory(parametros: ParametrosEdicion) = viewModelFactory {
            initializer {
                ExportarViewModel(this[APPLICATION_KEY] as KlippoApp, createSavedStateHandle(), parametros)
            }
        }
    }
}
