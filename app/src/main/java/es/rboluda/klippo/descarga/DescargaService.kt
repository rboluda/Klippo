package es.rboluda.klippo.descarga

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import com.yausername.youtubedl_android.YoutubeDL
import es.rboluda.klippo.KlippoApp
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.almacenamiento.MetaDescarga
import es.rboluda.klippo.almacenamiento.Miniaturas
import es.rboluda.klippo.sesion.Sesiones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Servicio en primer plano que ejecuta las descargas y muestra el progreso en notificación
class DescargaService : Service() {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trabajos = mutableMapOf<String, Job>()
    private var ultimaNotificacion = 0L

    // Como mucho unas pocas descargas a la vez: una lista de Spotify entera lanzaría decenas
    // de procesos de yt-dlp; las demás esperan en cola
    private val turnos = Semaphore(MAX_SIMULTANEAS)

    private val app get() = application as KlippoApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_DESCARGAR -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val calidad = intent.getStringExtra(EXTRA_CALIDAD)
                    ?.let { nombre -> Calidad.entries.find { it.name == nombre } }
                    ?: Calidad.MEJOR
                val id = intent.getStringExtra(EXTRA_ID) ?: nuevoId()
                val etiquetas = intent.getSerializableExtra(EXTRA_ETIQUETAS, EtiquetasAudio::class.java)
                if (url != null) iniciar(id, url, calidad, etiquetas) else detenerSiNoHayTrabajo()
            }
            ACCION_CANCELAR -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                app.motor.cancelar(id)
                // Si aún estaba en cola no hay proceso que parar: se cancela la espera
                if (app.descargas.buscar(id)?.enCola == true) trabajos[id]?.cancel()
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciar(id: String, url: String, calidad: Calidad, etiquetas: EtiquetasAudio?) {
        val descarga = Descarga(
            id = id,
            url = url,
            carpeta = File(app.carpetaDescargas, id),
            calidad = calidad,
            creado = etiquetas?.creado ?: System.currentTimeMillis(),
            // Lo que viene de Spotify ya sabe su nombre y su lista desde el principio
            titulo = etiquetas?.nombre,
            grupo = etiquetas?.grupo,
            lista = etiquetas?.lista,
            enCola = turnos.availablePermits == 0,
        )
        app.descargas.agregar(descarga)
        MetaDescarga.escribir(descarga)
        // Hay que pasar a primer plano nada más arrancar (límite de 5 s del sistema)
        startForeground(
            Notificaciones.ID_PROGRESO,
            Notificaciones.progreso(this, descarga, trabajos.size + 1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        trabajos[id] = ambito.launch {
            try {
                turnos.withPermit {
                    app.descargas.actualizar(id) { it.copy(enCola = false) }
                    ejecutar(descarga, etiquetas)
                }
            } catch (e: CancellationException) {
                // Cancelada mientras esperaba turno
                app.descargas.actualizar(id) { it.copy(estado = EstadoDescarga.Cancelada, enCola = false) }
                descarga.carpeta.deleteRecursively()
                trabajos.remove(id)
                detenerSiNoHayTrabajo()
            }
        }
    }

    private suspend fun ejecutar(descarga: Descarga, etiquetas: EtiquetasAudio?) {
        val id = descarga.id
        try {
            val cookies = Sesiones.cookiesPara(this, descarga.url)
            val resultado = app.motor.descargar(
                id, descarga.url, descarga.carpeta, cookies, descarga.calidad,
            ) { avance ->
                // Este callback llega desde el hilo del proceso de yt-dlp
                app.descargas.actualizar(id) {
                    it.copy(
                        progreso = avance.progreso,
                        etaSegundos = avance.etaSegundos,
                        elemento = avance.elemento,
                        totalElementos = avance.total,
                    )
                }
                ambito.launch { refrescarNotificacion(id, forzar = false) }
            }
            val archivos = resultado.archivos
            if (etiquetas != null) {
                completarEtiquetado(descarga, archivos.first(), etiquetas)
                return
            }
            // Lista o carrusel: todos sus vídeos comparten grupo y nombre de la lista (si
            // yt-dlp no lo da, el título del primero) para tratarlos en bloque
            val esLista = archivos.size > 1
            val grupo = if (esLista) id else null
            val lista = if (esLista) resultado.lista ?: resultado.titulo(archivos.first()) else null
            withContext(Dispatchers.IO) {
                // Los demás vídeos pasan a su propia carpeta: una tarjeta por vídeo
                archivos.drop(1).forEachIndexed { j, archivo ->
                    separar(descarga, archivo, j + 1, resultado.titulo(archivo), grupo, lista)
                }
            }
            val primero = archivos.first()
            val miniatura = withContext(Dispatchers.IO) {
                // La miniatura de un audio la deja yt-dlp con el nombre del archivo
                MotorDescarga.miniaturaDescargada(primero).renameTo(Miniaturas.archivo(descarga.carpeta))
                Miniaturas.obtener(descarga.carpeta, primero)
            }
            app.descargas.actualizar(id) {
                it.copy(
                    estado = EstadoDescarga.Completada,
                    progreso = 1f,
                    archivo = primero,
                    titulo = resultado.titulo(primero),
                    miniatura = miniatura,
                    grupo = grupo,
                    lista = lista,
                )
            }
            app.descargas.buscar(id)?.let { MetaDescarga.escribir(it) }
        } catch (e: YoutubeDL.CanceledException) {
            app.descargas.actualizar(id) { it.copy(estado = EstadoDescarga.Cancelada) }
            descarga.carpeta.deleteRecursively()
        } catch (e: Exception) {
            app.descargas.actualizar(id) {
                it.copy(estado = EstadoDescarga.Fallida(MotorDescarga.mensajeDeError(e)))
            }
        } finally {
            trabajos.remove(id)
            app.descargas.buscar(id)?.let(::avisarTerminada)
            detenerSiNoHayTrabajo()
        }
    }

    // Audio de una lista de Spotify: se le ponen los datos y la portada de Spotify
    private suspend fun completarEtiquetado(descarga: Descarga, archivo: File, etiquetas: EtiquetasAudio) {
        val portada = etiquetas.portada?.let(::File)?.takeIf { it.exists() }
        app.motor.etiquetar(archivo, etiquetas, portada)
        val miniatura = withContext(Dispatchers.IO) {
            val destino = Miniaturas.archivo(descarga.carpeta)
            val deYoutube = MotorDescarga.miniaturaDescargada(archivo)
            // La portada de Spotify sustituye a la imagen del vídeo de YouTube
            if (portada != null) {
                deYoutube.delete()
                portada.copyTo(destino, overwrite = true)
            } else {
                deYoutube.renameTo(destino)
            }
            destino.takeIf { it.exists() }
        }
        app.descargas.actualizar(descarga.id) {
            it.copy(estado = EstadoDescarga.Completada, progreso = 1f, archivo = archivo, miniatura = miniatura)
        }
        app.descargas.buscar(descarga.id)?.let { MetaDescarga.escribir(it) }
    }

    // Aviso final. En una lista que se baja canción a canción (Spotify) solo se avisa cuando
    // acaba la última, con el total; si no, uno por descarga
    private fun avisarTerminada(terminada: Descarga) {
        val grupo = terminada.grupo
        if (grupo == null) {
            Notificaciones.mostrarTerminada(this, terminada, 1)
            return
        }
        val delGrupo = app.descargas.descargas.value.filter { it.grupo == grupo }
        if (delGrupo.any { it.estado == EstadoDescarga.Descargando }) return
        val completadas = delGrupo.filter { it.estado == EstadoDescarga.Completada }
        if (completadas.size > 1) {
            Notificaciones.mostrarTerminada(this, completadas.first(), completadas.size)
        } else {
            Notificaciones.mostrarTerminada(this, terminada, 1)
        }
    }

    // Mueve el vídeo [posicion] de una lista o carrusel a su propia carpeta y lo añade como
    // descarga completada (justo debajo del primero en la lista)
    private fun separar(
        original: Descarga,
        archivo: File,
        posicion: Int,
        titulo: String?,
        grupo: String?,
        lista: String?,
    ) {
        val id = "${original.id}-${posicion + 1}"
        val carpeta = File(app.carpetaDescargas, id).apply { mkdirs() }
        val destino = File(carpeta, "video.${archivo.extension}")
        MotorDescarga.miniaturaDescargada(archivo).renameTo(Miniaturas.archivo(carpeta))
        if (!archivo.renameTo(destino)) return
        val nueva = original.copy(
            id = id,
            carpeta = carpeta,
            creado = original.creado - posicion,
            titulo = titulo,
            estado = EstadoDescarga.Completada,
            progreso = 1f,
            archivo = destino,
            miniatura = Miniaturas.obtener(carpeta, destino),
            grupo = grupo,
            lista = lista,
        )
        MetaDescarga.escribir(nueva)
        app.descargas.agregar(nueva)
    }

    // Actualiza la notificación de progreso como mucho dos veces por segundo
    private fun refrescarNotificacion(id: String, forzar: Boolean) {
        val ahora = SystemClock.elapsedRealtime()
        if (!forzar && ahora - ultimaNotificacion < 500) return
        ultimaNotificacion = ahora
        val descarga = app.descargas.buscar(id) ?: return
        getSystemService(NotificationManager::class.java)
            .notify(Notificaciones.ID_PROGRESO, Notificaciones.progreso(this, descarga, trabajos.size))
    }

    private fun detenerSiNoHayTrabajo() {
        if (trabajos.isNotEmpty()) {
            // Sigue otra descarga: la notificación pasa a mostrar esa
            refrescarNotificacion(trabajos.keys.first(), forzar = true)
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Android 15+ limita dataSync a 6 h al día: si se agota, se cancela todo
    override fun onTimeout(startId: Int, fgsType: Int) {
        trabajos.keys.toList().forEach { app.motor.cancelar(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ambito.cancel()
        super.onDestroy()
    }

    companion object {
        const val CARPETA_DESCARGAS = "descargas"
        private const val ACCION_DESCARGAR = "es.rboluda.klippo.DESCARGAR"
        private const val ACCION_CANCELAR = "es.rboluda.klippo.CANCELAR"
        private const val EXTRA_URL = "url"
        private const val EXTRA_CALIDAD = "calidad"
        private const val EXTRA_ID = "id"
        private const val EXTRA_ETIQUETAS = "etiquetas"
        private const val MAX_SIMULTANEAS = 2

        private fun nuevoId() = UUID.randomUUID().toString().substring(0, 8)

        // Lanza una descarga y devuelve su id. Con [etiquetas] (audio de una lista de Spotify)
        // el resultado se etiqueta con esos datos y se agrupa con el resto de la lista.
        fun descargar(context: Context, url: String, calidad: Calidad, etiquetas: EtiquetasAudio? = null): String {
            val id = nuevoId()
            val intent = Intent(context, DescargaService::class.java)
                .setAction(ACCION_DESCARGAR)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_CALIDAD, calidad.name)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_ETIQUETAS, etiquetas)
            context.startForegroundService(intent)
            return id
        }

        fun cancelar(context: Context, id: String) {
            context.startService(intentCancelar(context, id))
        }

        fun intentCancelar(context: Context, id: String): Intent =
            Intent(context, DescargaService::class.java)
                .setAction(ACCION_CANCELAR)
                .putExtra(EXTRA_ID, id)
    }
}
