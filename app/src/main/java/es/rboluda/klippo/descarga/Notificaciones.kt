package es.rboluda.klippo.descarga

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import es.rboluda.klippo.MainActivity
import es.rboluda.klippo.R
import es.rboluda.klippo.almacenamiento.Compartir
import java.io.File

// Canales y notificaciones de las descargas
object Notificaciones {

    private const val CANAL_PROGRESO = "progreso"
    private const val CANAL_TERMINADAS = "terminadas"
    const val ID_PROGRESO = 1

    fun crearCanales(context: Context) {
        val gestor = context.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannels(
            listOf(
                // Importancia baja: la barra de progreso no debe sonar ni vibrar
                NotificationChannel(
                    CANAL_PROGRESO,
                    context.getString(R.string.canal_progreso),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CANAL_TERMINADAS,
                    context.getString(R.string.canal_terminadas),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        )
    }

    fun progreso(context: Context, descarga: Descarga, activas: Int): Notification {
        val titulo = if (activas > 1) {
            context.resources.getQuantityString(R.plurals.notif_descargando_varias, activas, activas)
        } else {
            context.getString(R.string.notif_descargando)
        }
        val cancelar = PendingIntent.getService(
            context,
            descarga.id.hashCode(),
            DescargaService.intentCancelar(context, descarga.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val porcentaje = (descarga.progreso * 100).toInt()
        return Notification.Builder(context, CANAL_PROGRESO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(descarga.titulo ?: descarga.url)
            .setSubText(
                when {
                    descarga.progreso < 0f -> null
                    // Lista: porcentaje del total y elemento en curso ("40 % · 4/10")
                    descarga.totalElementos > 1 ->
                        "$porcentaje % · ${descarga.elemento}/${descarga.totalElementos}"
                    else -> "$porcentaje %"
                }
            )
            .setProgress(100, porcentaje.coerceAtLeast(0), descarga.progreso < 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(abrirApp(context, null))
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.cancelar), cancelar).build()
            )
            .build()
    }

    // Aviso final (completada o con error), útil cuando se comparte desde otra app
    // [elementos]: vídeos de la lista si la descarga era una lista o un carrusel
    fun mostrarTerminada(context: Context, descarga: Descarga, elementos: Int = 1) {
        val gestor = context.getSystemService(NotificationManager::class.java)
        if (!gestor.areNotificationsEnabled()) return
        val esLista = descarga.estado == EstadoDescarga.Completada && elementos > 1
        val (titulo, texto) = when (val estado = descarga.estado) {
            // Lista: su nombre y cuántos vídeos tiene; se gestiona en bloque desde la app
            EstadoDescarga.Completada -> if (esLista) {
                context.resources.getQuantityString(R.plurals.notif_lista_completada, elementos, elementos) to
                    (descarga.lista ?: descarga.url)
            } else {
                context.getString(R.string.notif_completada) to (descarga.titulo ?: descarga.url)
            }
            is EstadoDescarga.Fallida ->
                context.getString(R.string.notif_fallida) to estado.mensaje
            else -> return
        }
        val constructor = Notification.Builder(context, CANAL_TERMINADAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setAutoCancel(true)
        val archivo = descarga.archivo
        if (descarga.estado == EstadoDescarga.Completada && archivo != null && !esLista) {
            // Tocar la notificación abre el vídeo en el editor
            constructor.setContentIntent(abrirApp(context, descarga.id))
            accionesRapidas(context, descarga.id, archivo, descarga.titulo).forEach(constructor::addAction)
        } else {
            constructor.setContentIntent(abrirApp(context, null))
        }
        gestor.notify(descarga.id.hashCode(), constructor.build())
    }

    // WhatsApp (si está instalado), Compartir y Guardar en galería, sin abrir Klippo
    private fun accionesRapidas(context: Context, id: String, archivo: File, titulo: String?): List<Notification.Action> {
        // Cada acción necesita su propio requestCode para no pisar las de otras descargas
        fun actividad(intent: Intent, n: Int) = PendingIntent.getActivity(
            context,
            id.hashCode() * 4 + n,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return listOfNotNull(
            Compartir.intentWhatsApp(context, archivo)?.let {
                Notification.Action.Builder(null, context.getString(R.string.whatsapp), actividad(it, 1)).build()
            },
            Notification.Action.Builder(
                null,
                context.getString(R.string.compartir),
                actividad(Compartir.intentElegir(context, archivo, titulo), 2),
            ).build(),
            Notification.Action.Builder(
                null,
                context.getString(R.string.notif_guardar),
                Compartir.pendienteGuardar(context, archivo),
            ).build(),
        )
    }

    // [idAbrir]: descarga que se abrirá en el editor; null = solo abrir la app
    private fun abrirApp(context: Context, idAbrir: String?): PendingIntent = PendingIntent.getActivity(
        context,
        idAbrir?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { idAbrir?.let { putExtra(MainActivity.EXTRA_ABRIR, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
