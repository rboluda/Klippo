package es.rboluda.klippo.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.rboluda.klippo.KlippoApp
import es.rboluda.klippo.R
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.ajustes.Tema
import es.rboluda.klippo.ajustes.Vista
import es.rboluda.klippo.almacenamiento.LimpiezaCache
import es.rboluda.klippo.descarga.EstadoMotor
import es.rboluda.klippo.descarga.ResultadoActualizacion
import es.rboluda.klippo.sesion.Plataforma
import es.rboluda.klippo.sesion.Sesiones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AjustesViewModel(private val app: KlippoApp) : ViewModel() {

    private val ajustes = app.ajustes

    private fun <T> estado(flujo: Flow<T>, inicial: T): StateFlow<T> =
        flujo.stateIn(viewModelScope, SharingStarted.Eagerly, inicial)

    val tema = estado(ajustes.tema, Tema.SISTEMA)
    val coloresSistema = estado(ajustes.coloresSistema, false)
    val preguntarCalidad = estado(ajustes.preguntarCalidad, true)
    val calidad = estado(ajustes.calidad, Calidad.MEJOR)
    val vista = estado(ajustes.vista, Vista.LISTA)
    val buscarActualizaciones = estado(ajustes.buscarActualizaciones, true)
    val spotifyMp3 = estado(ajustes.spotifyMp3, false)

    fun elegirTema(valor: Tema) = guardar { ajustes.guardarTema(valor) }
    fun cambiarColoresSistema(valor: Boolean) = guardar { ajustes.guardarColoresSistema(valor) }
    fun cambiarPreguntarCalidad(valor: Boolean) = guardar { ajustes.guardarPreguntarCalidad(valor) }
    fun elegirCalidad(valor: Calidad) = guardar { ajustes.guardarCalidad(valor) }
    fun elegirVista(valor: Vista) = guardar { ajustes.guardarVista(valor) }
    fun elegirSpotifyMp3(valor: Boolean) = guardar { ajustes.guardarSpotifyMp3(valor) }
    fun cambiarBuscarActualizaciones(valor: Boolean) = guardar { ajustes.guardarBuscarActualizaciones(valor) }

    private fun guardar(accion: suspend () -> Unit) {
        viewModelScope.launch { accion() }
    }

    // Mensajes puntuales para el snackbar
    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos = _avisos.receiveAsFlow()

    // --- Motor ---

    val estadoMotor: StateFlow<EstadoMotor> = app.motor.estado

    private val _actualizandoMotor = MutableStateFlow(false)
    val actualizandoMotor: StateFlow<Boolean> = _actualizandoMotor.asStateFlow()

    fun actualizarMotor() {
        if (_actualizandoMotor.value) return
        _actualizandoMotor.value = true
        viewModelScope.launch {
            val texto = when (app.motor.actualizar()) {
                ResultadoActualizacion.ACTUALIZADO -> R.string.motor_actualizado
                ResultadoActualizacion.YA_AL_DIA -> R.string.motor_ya_al_dia
                ResultadoActualizacion.ERROR -> R.string.motor_error_actualizar
            }
            _actualizandoMotor.value = false
            _avisos.send(app.getString(texto))
        }
    }

    // --- Cuentas ---

    private val _sesiones = MutableStateFlow<Set<Plataforma>>(emptySet())
    val sesiones: StateFlow<Set<Plataforma>> = _sesiones.asStateFlow()

    fun refrescarSesiones() {
        _sesiones.value = Plataforma.entries.filter { Sesiones.tieneSesion(app, it) }.toSet()
    }

    fun cerrarSesion(plataforma: Plataforma) {
        Sesiones.cerrar(app, plataforma)
        refrescarSesiones()
        _avisos.trySend(app.getString(R.string.sesion_cerrada, plataforma.nombre))
    }

    // --- Almacenamiento ---

    private val _bytesCache = MutableStateFlow<Long?>(null)
    val bytesCache: StateFlow<Long?> = _bytesCache.asStateFlow()

    fun calcularCache() {
        viewModelScope.launch {
            _bytesCache.value = withContext(Dispatchers.IO) {
                app.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
    }

    // Borra ya todo lo de la caché salvo las descargas en curso
    fun vaciarCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LimpiezaCache.limpiar(
                    app.cacheDir, app.carpetaDescargas, app.descargas.idsActivos(), edadMaximaMs = 0,
                )
                app.descargas.sincronizar(app.carpetaDescargas)
            }
            calcularCache()
            _avisos.send(app.getString(R.string.ajustes_cache_vaciada))
        }
    }

    // --- Actualizaciones de Klippo ---

    val actualizacionesActivas: Boolean = app.actualizador.activo

    fun buscarActualizacionesAhora() {
        viewModelScope.launch {
            val texto = try {
                if (app.actualizador.comprobar() == null) {
                    app.getString(R.string.actualizacion_al_dia)
                } else {
                    app.getString(R.string.actualizacion_ver_inicio)
                }
            } catch (e: Exception) {
                app.getString(R.string.actualizacion_error_comprobar)
            }
            _avisos.send(texto)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AjustesViewModel(this[APPLICATION_KEY] as KlippoApp) }
        }
    }
}
