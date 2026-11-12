package es.rboluda.klippo.ui.spotify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.rboluda.klippo.KlippoApp
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.DescargaService
import es.rboluda.klippo.descarga.EstadoDescarga
import es.rboluda.klippo.descarga.EtiquetasAudio
import es.rboluda.klippo.descarga.PermisoNotificaciones
import es.rboluda.klippo.spotify.Coincidencia
import es.rboluda.klippo.spotify.Coincidencias
import es.rboluda.klippo.spotify.LectorSpotify
import es.rboluda.klippo.spotify.ListaSpotify
import es.rboluda.klippo.spotify.PistaSpotify
import es.rboluda.klippo.spotify.Similitud
import es.rboluda.klippo.spotify.TipoSpotify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface EstadoSpotify {
    data object Cargando : EstadoSpotify
    data class Error(val mensaje: String) : EstadoSpotify
    data class Lista(val lista: ListaSpotify, val portada: File?) : EstadoSpotify
}

// Una canción de la lista con lo encontrado en YouTube (null = aún buscando) y su descarga
data class FilaPista(
    val pista: PistaSpotify,
    val coincidencia: Coincidencia? = null,
    val idDescarga: String? = null,
) {
    // Se puede bajar si se ha encontrado algo y no se está bajando ya (o falló)
    fun descargable(descargas: Map<String, Descarga>): Boolean {
        val candidato = coincidencia?.candidato ?: return false
        if (candidato.id.isEmpty()) return false
        val estado = idDescarga?.let { descargas[it]?.estado } ?: return true
        return estado is EstadoDescarga.Fallida || estado == EstadoDescarga.Cancelada
    }
}

/**
 * Pantalla "Descargar desde Spotify": lee la lista de Spotify, busca cada canción en YouTube,
 * dice lo parecido que es lo encontrado y lanza las descargas (solo audio) con los datos y la
 * portada de Spotify, todas agrupadas como una lista de Klippo.
 */
class SpotifyViewModel(private val app: KlippoApp, estadoGuardado: SavedStateHandle) : ViewModel() {

    private val url: String = estadoGuardado.get<String>(ARG_URL).orEmpty()

    private val _estado = MutableStateFlow<EstadoSpotify>(EstadoSpotify.Cargando)
    val estado: StateFlow<EstadoSpotify> = _estado.asStateFlow()

    private val _filas = MutableStateFlow<List<FilaPista>>(emptyList())
    val filas: StateFlow<List<FilaPista>> = _filas.asStateFlow()

    // Descargas de la app por id, para mostrar el avance de cada canción
    val descargas: StateFlow<Map<String, Descarga>> = app.descargas.descargas
        .map { lista -> lista.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val mp3: StateFlow<Boolean> = app.ajustes.spotifyMp3.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Guardar en la galería al terminar cada canción (se recuerda)
    val guardarEnGaleria: StateFlow<Boolean> =
        app.ajustes.spotifyGaleria.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun cambiarGuardarEnGaleria(activo: Boolean) {
        viewModelScope.launch { app.ajustes.guardarSpotifyGaleria(activo) }
    }

    // Todas las descargas de esta lista comparten grupo; el momento de apertura ordena las
    // canciones en Klippo en el mismo orden que en Spotify
    private val momento = System.currentTimeMillis()
    private val idBusqueda = "spotify-$momento"

    init {
        cargar()
    }

    fun cargar() {
        _estado.value = EstadoSpotify.Cargando
        viewModelScope.launch {
            val lista = try {
                LectorSpotify.leer(url)
            } catch (e: Exception) {
                _estado.value = EstadoSpotify.Error(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            val portada = lista.portadaUrl?.let { u ->
                LectorSpotify.descargarPortada(u, File(app.cacheDir, "spotify/${lista.enlace.id}.jpg"))
            }
            _filas.value = lista.pistas.map { FilaPista(it) }
            _estado.value = EstadoSpotify.Lista(lista, portada)
            buscar(lista.pistas)
        }
    }

    private suspend fun buscar(pistas: List<PistaSpotify>) {
        // "Artista - Título" con el intérprete principal y el título sin "(feat…)" ni "Remastered"
        val consultas = pistas.groupBy { consulta(it) }
        try {
            app.motor.buscarEnYoutube(idBusqueda, consultas.keys.toList()) { texto, candidatos ->
                val ids = consultas[texto].orEmpty().associateBy { it.id }
                _filas.update { filas ->
                    filas.map { fila ->
                        ids[fila.pista.id]?.let { fila.copy(coincidencia = Coincidencias.evaluar(it, candidatos)) } ?: fila
                    }
                }
            }
        } catch (e: Exception) {
            // Motor no listo o búsqueda cancelada: lo que no haya llegado queda como no encontrado
            _filas.update { filas ->
                filas.map { it.coincidencia?.let { _ -> it } ?: it.copy(coincidencia = Coincidencia(Similitud.NO_ENCONTRADA, null)) }
            }
        }
    }

    fun debePedirPermisoNotificaciones(): Boolean = PermisoNotificaciones.debePedir(app)

    fun marcarPermisoPedido() = PermisoNotificaciones.marcarPedido(app)

    fun descargar(idPista: String) {
        val fila = _filas.value.find { it.pista.id == idPista } ?: return
        viewModelScope.launch { lanzar(listOf(fila)) }
    }

    // Todas las encontradas (idénticas y parecidas) que no se estén bajando ya
    fun descargarTodas() {
        val actuales = descargas.value
        val pendientes = _filas.value.filter { it.descargable(actuales) }
        if (pendientes.isEmpty()) return
        viewModelScope.launch { lanzar(pendientes) }
    }

    private suspend fun lanzar(filas: List<FilaPista>) {
        val lista = (_estado.value as? EstadoSpotify.Lista) ?: return
        val calidad = if (app.ajustes.spotifyMp3.first()) Calidad.AUDIO_MP3 else Calidad.AUDIO
        val guardar = app.ajustes.spotifyGaleria.first()
        val individual = lista.lista.enlace.tipo == TipoSpotify.TRACK
        val nuevas = filas.mapNotNull { fila ->
            val candidato = fila.coincidencia?.candidato ?: return@mapNotNull null
            val pista = fila.pista
            val etiquetas = EtiquetasAudio(
                nombre = pista.nombreCompleto,
                titulo = pista.titulo,
                artista = pista.artistas.joinToString(", ").ifBlank { null },
                // En una lista, su nombre hace de álbum: el reproductor las agrupa igual que la carpeta
                album = if (individual) null else lista.lista.nombre,
                pista = if (individual) null else pista.numero,
                totalPistas = if (individual) null else lista.lista.pistas.size,
                portada = lista.portada?.absolutePath,
                grupo = if (individual) null else "spotify-${lista.lista.enlace.id}-$momento",
                lista = if (individual) null else lista.lista.nombre,
                creado = momento - pista.numero,
                alternativas = fila.coincidencia.alternativas.take(MAX_ALTERNATIVAS).map { it.url },
                guardarEnGaleria = guardar,
            )
            pista.id to DescargaService.descargar(app, candidato.url, calidad, etiquetas)
        }.toMap()
        _filas.update { actuales -> actuales.map { f -> nuevas[f.pista.id]?.let { f.copy(idDescarga = it) } ?: f } }
    }

    override fun onCleared() {
        app.motor.cancelarBusqueda(idBusqueda)
    }

    companion object {
        const val ARG_URL = "url"

        // Otros resultados que se prueban si el elegido falla
        private const val MAX_ALTERNATIVAS = 3

        fun consulta(pista: PistaSpotify): String =
            listOfNotNull(pista.artistaPrincipal, Coincidencias.tituloBase(pista.titulo)).joinToString(" - ")

        val Factory = viewModelFactory {
            initializer { SpotifyViewModel(this[APPLICATION_KEY] as KlippoApp, createSavedStateHandle()) }
        }
    }
}
