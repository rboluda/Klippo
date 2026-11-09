package es.rboluda.klippo.ajustes

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.rboluda.klippo.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Calidad máxima de descarga; null = la mejor disponible
enum class Calidad(
    val alturaMax: Int?,
    @param:StringRes val etiqueta: Int,
    @param:StringRes val etiquetaCorta: Int,
) {
    MEJOR(null, R.string.calidad_mejor, R.string.calidad_mejor_corta),
    P1080(1080, R.string.calidad_1080, R.string.calidad_1080_corta),
    P720(720, R.string.calidad_720, R.string.calidad_720_corta),
    P480(480, R.string.calidad_480, R.string.calidad_480_corta),
    // Solo la pista de audio, en M4A (AAC) sin recodificar
    AUDIO(null, R.string.calidad_audio, R.string.calidad_audio_corta),
    // Solo audio en MP3: se recodifica (pierde algo), pero lo reproduce cualquier aparato
    AUDIO_MP3(null, R.string.calidad_audio_mp3, R.string.calidad_audio_mp3_corta),
    ;

    val soloAudio: Boolean get() = this == AUDIO || this == AUDIO_MP3

    // Formato del archivo de audio (solo si soloAudio)
    val formatoAudio: String get() = if (this == AUDIO_MP3) "mp3" else "m4a"
}

// Cómo se muestran las descargas en Inicio
enum class Vista { LISTA, MOSAICO }

// Claro, oscuro o lo que diga el sistema
enum class Tema(@param:StringRes val etiqueta: Int) {
    SISTEMA(R.string.ajustes_tema_sistema),
    CLARO(R.string.ajustes_tema_claro),
    OSCURO(R.string.ajustes_tema_oscuro),
}

private val Context.almacen by preferencesDataStore(name = "ajustes")

// Últimos ajustes usados: se aplican también a los enlaces recibidos con Compartir
class AjustesRepositorio(private val context: Context) {

    private val claveCalidad = stringPreferencesKey("calidad")

    val calidad: Flow<Calidad> = context.almacen.data.map { prefs ->
        prefs[claveCalidad]?.let { nombre -> Calidad.entries.find { it.name == nombre } } ?: Calidad.MEJOR
    }

    suspend fun calidadActual(): Calidad = calidad.first()

    suspend fun guardarCalidad(calidad: Calidad) {
        context.almacen.edit { it[claveCalidad] = calidad.name }
    }

    private val claveVista = stringPreferencesKey("vista")

    val vista: Flow<Vista> = context.almacen.data.map { prefs ->
        prefs[claveVista]?.let { nombre -> Vista.entries.find { it.name == nombre } } ?: Vista.LISTA
    }

    suspend fun guardarVista(vista: Vista) {
        context.almacen.edit { it[claveVista] = vista.name }
    }

    // Momento de la última consulta a GitHub por actualizaciones (ms desde epoch)
    private val claveUltimaComprobacion = longPreferencesKey("ultima_comprobacion")

    suspend fun ultimaComprobacion(): Long =
        context.almacen.data.map { it[claveUltimaComprobacion] ?: 0L }.first()

    suspend fun guardarUltimaComprobacion(momento: Long) {
        context.almacen.edit { it[claveUltimaComprobacion] = momento }
    }

    // --- Pantalla de Ajustes ---

    private val claveTema = stringPreferencesKey("tema")
    private val claveColoresSistema = booleanPreferencesKey("colores_sistema")
    private val clavePreguntarCalidad = booleanPreferencesKey("preguntar_calidad")
    private val claveBuscarActualizaciones = booleanPreferencesKey("buscar_actualizaciones")

    val tema: Flow<Tema> = context.almacen.data.map { prefs ->
        prefs[claveTema]?.let { nombre -> Tema.entries.find { it.name == nombre } } ?: Tema.SISTEMA
    }

    // false = colores de Klippo; true = colores del fondo de pantalla (Material You)
    val coloresSistema: Flow<Boolean> = context.almacen.data.map { it[claveColoresSistema] ?: false }

    // false = se descarga directamente con la calidad predeterminada (la última elegida)
    val preguntarCalidad: Flow<Boolean> = context.almacen.data.map { it[clavePreguntarCalidad] ?: true }

    val buscarActualizaciones: Flow<Boolean> =
        context.almacen.data.map { it[claveBuscarActualizaciones] ?: true }

    suspend fun guardarTema(tema: Tema) {
        context.almacen.edit { it[claveTema] = tema.name }
    }

    suspend fun guardarColoresSistema(activo: Boolean) {
        context.almacen.edit { it[claveColoresSistema] = activo }
    }

    suspend fun guardarPreguntarCalidad(activo: Boolean) {
        context.almacen.edit { it[clavePreguntarCalidad] = activo }
    }

    suspend fun guardarBuscarActualizaciones(activo: Boolean) {
        context.almacen.edit { it[claveBuscarActualizaciones] = activo }
    }
}
