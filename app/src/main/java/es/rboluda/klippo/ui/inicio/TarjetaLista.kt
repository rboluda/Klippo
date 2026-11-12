package es.rboluda.klippo.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.rboluda.klippo.R
import es.rboluda.klippo.almacenamiento.Compartir
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.EstadoDescarga

// Elementos de la pantalla de inicio: una descarga suelta o una lista (playlist o carrusel)
// que agrupa varias descargas completadas
sealed interface EntradaInicio {
    val clave: String

    data class Unica(val descarga: Descarga) : EntradaInicio {
        override val clave get() = descarga.id
    }

    data class Lista(val grupo: String, val nombre: String, val elementos: List<Descarga>) : EntradaInicio {
        override val clave get() = "lista-$grupo"
    }
}

// Agrupa los vídeos de una misma lista en el lugar del primero, conservando el orden
fun agrupar(descargas: List<Descarga>): List<EntradaInicio> {
    val porGrupo = descargas.filter { it.grupo != null }.groupBy { it.grupo!! }
    val vistos = mutableSetOf<String>()
    return descargas.mapNotNull { descarga ->
        val grupo = descarga.grupo
        when {
            grupo == null -> EntradaInicio.Unica(descarga)
            !vistos.add(grupo) -> null
            else -> {
                val elementos = porGrupo.getValue(grupo)
                // Una lista de la que solo queda un vídeo se muestra como uno suelto
                if (elementos.size == 1) {
                    EntradaInicio.Unica(descarga)
                } else {
                    EntradaInicio.Lista(grupo, descarga.lista ?: descarga.titulo ?: descarga.url, elementos)
                }
            }
        }
    }
}

/**
 * Tarjeta de una lista descargada: se trata en bloque (guardar todo en una carpeta de la
 * galería con el nombre de la lista, o compartir todo de una vez) y, si se quiere, se
 * despliega para ver cada vídeo con sus propios accesos directos.
 */
@Composable
fun TarjetaLista(
    lista: EntradaInicio.Lista,
    desplegada: Boolean,
    // Archivos ya guardados si se está guardando la lista; null si no
    guardados: Int?,
    onDesplegar: () -> Unit,
    onGuardarTodo: () -> Unit,
    // Cancela las que aún se están bajando (listas de Spotify, que se bajan canción a canción)
    onCancelar: () -> Unit,
) {
    val context = LocalContext.current
    // Las canceladas ya no cuentan como parte de la lista
    val elementos = lista.elementos.filter { it.estado != EstadoDescarga.Cancelada }
    val total = elementos.size
    val completadas = elementos.filter { it.estado == EstadoDescarga.Completada }
    val enCurso = elementos.filter { it.estado == EstadoDescarga.Descargando }
    val fallidas = elementos.count { it.estado is EstadoDescarga.Fallida }
    // Mientras se baja aún no hay archivo: se cuenta como audio si todas lo son o no se sabe
    val soloAudio = elementos.all { it.esAudio || it.archivo == null }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDesplegar),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniaturaVideo(
                    archivo = lista.elementos.firstNotNullOfOrNull { it.miniatura },
                    modifier = Modifier
                        .size(width = 72.dp, height = 96.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = lista.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            if (soloAudio) R.plurals.lista_canciones else R.plurals.lista_videos,
                            total,
                            total,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (fallidas > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.lista_con_error, fallidas, fallidas),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onDesplegar) {
                        Text(stringResource(if (desplegada) R.string.lista_ocultar else R.string.lista_ver))
                    }
                }
            }
            if (enCurso.isNotEmpty()) {
                // Avance de toda la lista: las terminadas más lo que lleven las que se bajan
                val avance = (completadas.size + enCurso.sumOf { it.progreso.coerceAtLeast(0f).toDouble() }) /
                    total.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.lista_descargando, completadas.size, total),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
                }
                LinearProgressIndicator(progress = { avance.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onGuardarTodo,
                    enabled = guardados == null && completadas.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (guardados != null) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.lista_guardando, (guardados + 1).coerceAtMost(total), total),
                            Modifier.padding(start = 8.dp),
                            maxLines = 1,
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_guardar_galeria), null, Modifier.size(18.dp))
                        Text(stringResource(R.string.lista_guardar_todo), Modifier.padding(start = 6.dp), maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = {
                        // En el orden de la lista (el primero es el más reciente en la pantalla)
                        val archivos = completadas.sortedByDescending { it.creado }.mapNotNull { it.archivo }
                        Compartir.varios(context, archivos, lista.nombre)
                    },
                    enabled = completadas.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_compartir), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.lista_compartir_todo), Modifier.padding(start = 6.dp), maxLines = 1)
                }
            }
        }
    }
}
