package es.rboluda.klippo.ui.inicio

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.rboluda.klippo.KlippoApp
import es.rboluda.klippo.R
import es.rboluda.klippo.actualizacion.EstadoActualizacion
import es.rboluda.klippo.actualizacion.VersionNueva
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.ajustes.Vista
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.DescargaService
import es.rboluda.klippo.descarga.PermisoNotificaciones
import es.rboluda.klippo.descarga.EstadoMotor
import es.rboluda.klippo.descarga.MotorDescarga
import es.rboluda.klippo.descarga.ResultadoActualizacion
import es.rboluda.klippo.sesion.Plataforma
import es.rboluda.klippo.sesion.Sesiones
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import es.rboluda.klippo.descarga.EstadoDescarga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import es.rboluda.klippo.almacenamiento.GuardarEnGaleria
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class InicioViewModel(private val app: KlippoApp) : ViewModel() {

    private val motor: MotorDescarga = app.motor

    val estadoMotor: StateFlow<EstadoMotor> = motor.estado
    val descargas: StateFlow<List<Descarga>> = app.descargas.descargas

    // La calidad se pregunta en cada vídeo; la última elegida queda marcada la próxima vez
    fun descargar(url: String, calidad: Calidad) {
        ultimaUrlPegada = url.trim()
        viewModelScope.launch { app.ajustes.guardarCalidad(calidad) }
        DescargaService.descargar(app, url.trim(), calidad)
    }

    val ultimaCalidad: StateFlow<Calidad> = app.ajustes.calidad
        .stateIn(viewModelScope, SharingStarted.Eagerly, Calidad.MEJOR)

    // Si está apagado en Ajustes, se descarga directamente con ultimaCalidad
    val preguntarCalidad: StateFlow<Boolean> = app.ajustes.preguntarCalidad
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Actualizaciones de la app desde GitHub Releases (solo en releases firmadas)
    val actualizacion: StateFlow<EstadoActualizacion> = app.actualizador.estado

    fun actualizarApp(nueva: VersionNueva) {
        viewModelScope.launch { app.actualizador.descargarEInstalar(nueva) }
    }

    // Lista o mosaico; se recuerda entre aperturas
    val vista: StateFlow<Vista> = app.ajustes.vista
        .stateIn(viewModelScope, SharingStarted.Eagerly, Vista.LISTA)

    fun alternarVista() {
        val nueva = if (vista.value == Vista.LISTA) Vista.MOSAICO else Vista.LISTA
        viewModelScope.launch { app.ajustes.guardarVista(nueva) }
    }

    // Enlace recibido con Compartir → "Klippo" (la entrada que abre la app); la pantalla
    // lo consume, lo descarga y, al terminar, abre el editor
    private val _urlCompartida = MutableStateFlow<String?>(null)
    val urlCompartida: StateFlow<String?> = _urlCompartida.asStateFlow()

    fun recibirCompartido(url: String) {
        ultimaUrlPegada = url
        _urlCompartida.value = url
    }

    fun compartidoConsumido() {
        _urlCompartida.value = null
    }

    // Enlace cuya descarga, al completarse, debe abrir el editor automáticamente
    private var abrirAlTerminar: String? = null

    fun abrirEditorAlTerminar(url: String) {
        abrirAlTerminar = url
    }

    // Descarga pedida desde la notificación de "completada"
    private val _abrirPorId = Channel<String>(Channel.BUFFERED)

    fun abrirDescarga(id: String) {
        _abrirPorId.trySend(id)
    }

    // Emite el id de la descarga que hay que abrir en el editor (una sola vez)
    val abrirEditor: Flow<String> = merge(
        descargas
            .mapNotNull { lista ->
                val url = abrirAlTerminar ?: return@mapNotNull null
                lista.firstOrNull { it.url == url && it.estado == EstadoDescarga.Completada }?.id
            }
            .onEach { abrirAlTerminar = null },
        _abrirPorId.receiveAsFlow(),
    )

    // Evita volver a pegar del portapapeles un enlace ya usado
    private var ultimaUrlPegada: String? = null

    fun debeAutorrellenar(url: String): Boolean {
        if (url == ultimaUrlPegada || descargas.value.any { it.url == url }) return false
        ultimaUrlPegada = url
        return true
    }

    // Los audios se abren directamente en Exportar (el editor es para vídeo)
    fun esAudio(id: String): Boolean = app.descargas.buscar(id)?.esAudio == true

    fun cancelar(id: String) {
        DescargaService.cancelar(app, id)
    }

    // Listas que se están guardando en la galería: grupo → archivos guardados hasta ahora
    private val _guardandoListas = MutableStateFlow<Map<String, Int>>(emptyMap())
    val guardandoListas: StateFlow<Map<String, Int>> = _guardandoListas.asStateFlow()

    /**
     * Guarda en la galería todos los vídeos (o canciones) de una lista, dentro de una carpeta
     * con el nombre de la lista y cada archivo con su título: Movies/Klippo/<lista>/<título>.mp4
     * (o Music/… si es audio). Se hace aquí y no en la tarjeta para que siga aunque se cambie
     * de pantalla.
     */
    fun guardarLista(grupo: String) {
        if (grupo in _guardandoListas.value) return
        val elementos = descargas.value
            .filter { it.grupo == grupo && it.estado == EstadoDescarga.Completada }
            .sortedByDescending { it.creado }
        val lista = elementos.firstOrNull()?.lista ?: return
        _guardandoListas.update { it + (grupo to 0) }
        viewModelScope.launch {
            var guardados = 0
            elementos.forEachIndexed { i, descarga ->
                val archivo = descarga.archivo
                if (archivo != null) {
                    val titulo = descarga.titulo ?: "$lista ${i + 1}"
                    runCatching {
                        GuardarEnGaleria.guardar(
                            app, archivo, lista, titulo,
                            pista = descarga.pista ?: (i + 1),
                            totalPistas = descarga.totalPistas ?: elementos.size,
                        )
                    }
                        .onSuccess { guardados++ }
                }
                _guardandoListas.update { it + (grupo to i + 1) }
            }
            _guardandoListas.update { it - grupo }
            val carpeta = GuardarEnGaleria.nombreValido(lista).orEmpty()
            _avisos.send(
                if (guardados == elementos.size) {
                    app.getString(R.string.lista_guardada, carpeta)
                } else {
                    app.getString(R.string.lista_guardada_errores, guardados, elementos.size, carpeta)
                }
            )
        }
    }

    fun debePedirPermisoNotificaciones(): Boolean = PermisoNotificaciones.debePedir(app)

    fun marcarPermisoPedido() = PermisoNotificaciones.marcarPedido(app)

    private val _actualizando = MutableStateFlow(false)
    val actualizando: StateFlow<Boolean> = _actualizando.asStateFlow()

    // Mensajes puntuales para el snackbar
    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos = _avisos.receiveAsFlow()

    // Días desde la publicación de la versión de yt-dlp (se calcula en local, sin red)
    val diasMotor: StateFlow<Int?> = motor.estado
        .map { (it as? EstadoMotor.Listo)?.version?.let(::diasDesdeVersion) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Si se indica [reintentar], tras actualizar se relanza esa descarga fallida
    fun actualizarMotor(reintentar: Descarga? = null) {
        if (_actualizando.value) return
        _actualizando.value = true
        viewModelScope.launch {
            val resultado = motor.actualizar()
            @StringRes val aviso = when (resultado) {
                ResultadoActualizacion.ACTUALIZADO -> R.string.motor_actualizado
                ResultadoActualizacion.YA_AL_DIA -> R.string.motor_ya_al_dia
                ResultadoActualizacion.ERROR -> R.string.motor_error_actualizar
            }
            _actualizando.value = false
            if (reintentar != null && resultado != ResultadoActualizacion.ERROR) {
                app.descargas.eliminar(reintentar.id)
                withContext(Dispatchers.IO) { reintentar.carpeta.deleteRecursively() }
                descargar(reintentar.url, reintentar.calidad)
            }
            _avisos.send(app.getString(aviso))
        }
    }

    companion object {
        const val DIAS_MOTOR_ANTIGUO = 30

        // Las versiones de yt-dlp son fechas: 2026.10.20 (o 2026.10.20.123456 en nightly)
        private val FECHA_VERSION = Regex("""^(\d{4})\.(\d{1,2})\.(\d{1,2})""")

        private fun diasDesdeVersion(version: String): Int? {
            val (anio, mes, dia) = FECHA_VERSION.find(version)?.destructured ?: return null
            return try {
                val fecha = LocalDate.of(anio.toInt(), mes.toInt(), dia.toInt())
                ChronoUnit.DAYS.between(fecha, LocalDate.now()).toInt()
            } catch (e: Exception) {
                null
            }
        }

        val Factory = viewModelFactory {
            initializer { InicioViewModel(this[APPLICATION_KEY] as KlippoApp) }
        }
    }
}
