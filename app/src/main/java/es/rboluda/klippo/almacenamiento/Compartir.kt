package es.rboluda.klippo.almacenamiento

import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.chooser.ChooserAction
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import es.rboluda.klippo.R
import java.io.File

// Comparte un vídeo (o audio) de la caché con el share sheet del sistema o con WhatsApp.
// Los intents se exponen para usarlos también desde las acciones de la notificación.
object Compartir {

    // WhatsApp normal y Business (declarados en <queries> del manifest para poder detectarlos)
    private val PAQUETES_WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun video(context: Context, video: File, titulo: String?) {
        context.startActivity(intentElegir(context, video, titulo))
    }

    // Envía el archivo a WhatsApp a través del menú del sistema filtrado (ver intentWhatsApp).
    // Si Android no puede abrirlo, se muestra el motivo en vez de no hacer nada.
    fun aWhatsApp(context: Context, video: File) {
        val intent = intentWhatsApp(context, video)
        try {
            checkNotNull(intent) { context.getString(R.string.whatsapp_no_instalado) }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.whatsapp_error, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Paquete de WhatsApp instalado, o null si no hay ninguno
    fun whatsapp(context: Context): String? = PAQUETES_WHATSAPP.firstOrNull { paquete ->
        runCatching { context.packageManager.getPackageInfo(paquete, 0) }.isSuccess
    }

    // Share sheet del sistema; en Android 14+ incluye "Guardar en galería" como acción propia
    fun intentElegir(context: Context, video: File, titulo: String?): Intent {
        val selector = Intent.createChooser(envio(context, video, titulo), null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            selector.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, arrayOf(accionGuardar(context, video, titulo)))
        }
        return selector
    }

    /**
     * WhatsApp cierra sin avisar su pantalla de compartir si otra app la abre directamente
     * (comprobado en Android 16 con WhatsApp 2.26); en cambio funciona desde el menú del
     * sistema. Por eso se abre ese menú pero excluyendo todas las apps salvo WhatsApp: queda
     * solo WhatsApp y, normalmente, sus chats recientes como accesos directos.
     */
    fun intentWhatsApp(context: Context, video: File): Intent? {
        whatsapp(context) ?: return null
        val envio = envio(context, video, null)
        // Para ver todas las apps que aceptan el archivo hace falta <queries> en el manifest
        val excluidas = context.packageManager.queryIntentActivities(envio, 0)
            .map { it.activityInfo }
            .filterNot { it.packageName in PAQUETES_WHATSAPP }
            .map { ComponentName(it.packageName, it.name) }
        return Intent.createChooser(envio, context.getString(R.string.whatsapp))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluidas.toTypedArray())
    }

    // Broadcast que guarda el archivo en la galería sin abrir nada
    fun pendienteGuardar(context: Context, video: File, titulo: String? = null): PendingIntent = PendingIntent.getBroadcast(
        context,
        video.absolutePath.hashCode(),
        Intent(context, GuardarEnGaleriaReceiver::class.java)
            .putExtra(GuardarEnGaleriaReceiver.EXTRA_RUTA, video.absolutePath)
            .putExtra(GuardarEnGaleriaReceiver.EXTRA_TITULO, titulo),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Comparte varios archivos de una vez (una lista entera) con el menú del sistema
    fun varios(context: Context, archivos: List<File>, titulo: String?) {
        if (archivos.isEmpty()) return
        val uris = archivos.map { FileProvider.getUriForFile(context, "${context.packageName}.archivos", it) }
        val envio = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = archivos.map(::tipo).distinct().singleOrNull() ?: "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            titulo?.let { putExtra(Intent.EXTRA_TITLE, it) }
            // ClipData con todos los archivos: así el permiso de lectura llega a cada uno
            clipData = ClipData.newRawUri(titulo, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(envio, titulo))
    }

    // Tipo MIME según la extensión: vídeo MP4, audio M4A o MP3
    fun tipo(archivo: File): String = when (archivo.extension) {
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> "video/mp4"
    }

    private fun envio(context: Context, video: File, titulo: String?): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", video)
        return Intent(Intent.ACTION_SEND).apply {
            type = tipo(video)
            putExtra(Intent.EXTRA_STREAM, uri)
            titulo?.let { putExtra(Intent.EXTRA_TITLE, it) }
            // ClipData hace que el permiso de lectura llegue también a la vista previa del chooser
            clipData = ClipData.newRawUri(titulo, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun accionGuardar(context: Context, video: File, titulo: String?): ChooserAction =
        ChooserAction.Builder(
            Icon.createWithResource(context, R.drawable.ic_guardar_galeria),
            context.getString(R.string.galeria_guardar),
            pendienteGuardar(context, video, titulo),
        ).build()
}
