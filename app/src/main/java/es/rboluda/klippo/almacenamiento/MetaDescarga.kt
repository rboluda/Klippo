package es.rboluda.klippo.almacenamiento

import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.EstadoDescarga
import es.rboluda.klippo.descarga.MotorDescarga
import org.json.JSONObject
import java.io.File

// meta.json de cada descarga: permite reconstruir la lista tras cerrar la app
object MetaDescarga {

    private const val ARCHIVO = "meta.json"

    fun escribir(descarga: Descarga) {
        val json = JSONObject()
            .put("url", descarga.url)
            .put("creado", descarga.creado)
            .put("titulo", descarga.titulo)
            .put("grupo", descarga.grupo)
            .put("lista", descarga.lista)
            .put("pista", descarga.pista)
            .put("totalPistas", descarga.totalPistas)
        descarga.carpeta.mkdirs()
        File(descarga.carpeta, ARCHIVO).writeText(json.toString())
    }

    // Título guardado de la descarga que hay en [carpeta], si lo hay
    fun leerTitulo(carpeta: File?): String? = try {
        carpeta?.let { File(it, ARCHIVO) }?.takeIf { it.exists() }
            ?.let { JSONObject(it.readText()).optString("titulo").ifBlank { null } }
    } catch (e: Exception) {
        null
    }

    // Solo devuelve descargas completadas (carpeta con meta.json y un MP4 o M4A)
    fun leerCompletada(carpeta: File): Descarga? {
        val meta = File(carpeta, ARCHIVO).takeIf { it.exists() } ?: return null
        val video = carpeta.listFiles { f -> f.extension in MotorDescarga.EXTENSIONES }
            ?.maxByOrNull { it.length() }
            ?: return null
        return try {
            val json = JSONObject(meta.readText())
            Descarga(
                id = carpeta.name,
                url = json.getString("url"),
                carpeta = carpeta,
                creado = json.optLong("creado", carpeta.lastModified()),
                titulo = json.optString("titulo").ifBlank { null },
                grupo = json.optString("grupo").ifBlank { null },
                lista = json.optString("lista").ifBlank { null },
                pista = json.optInt("pista", 0).takeIf { it > 0 },
                totalPistas = json.optInt("totalPistas", 0).takeIf { it > 0 },
                estado = EstadoDescarga.Completada,
                progreso = 1f,
                archivo = video,
                // Las descargas anteriores a las miniaturas la generan aquí la primera vez
                miniatura = Miniaturas.obtener(carpeta, video),
            )
        } catch (e: Exception) {
            null
        }
    }
}
