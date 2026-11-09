package es.rboluda.klippo.almacenamiento

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import es.rboluda.klippo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Recibe la acción "Guardar en galería" del share sheet (ChooserAction, Android 14+)
class GuardarEnGaleriaReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruta = intent.getStringExtra(EXTRA_RUTA) ?: return
        val app = context.applicationContext
        // goAsync permite terminar la copia fuera del hilo principal
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val texto = try {
                GuardarEnGaleria.guardar(app, File(ruta), titulo = intent.getStringExtra(EXTRA_TITULO))
                app.getString(R.string.galeria_guardado)
            } catch (e: Exception) {
                app.getString(R.string.galeria_error)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, texto, Toast.LENGTH_SHORT).show() }
            pendiente.finish()
        }
    }

    companion object {
        const val EXTRA_RUTA = "ruta"
        const val EXTRA_TITULO = "titulo"
    }
}
