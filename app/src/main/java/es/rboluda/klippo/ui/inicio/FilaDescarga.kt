package es.rboluda.klippo.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.rboluda.klippo.R
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.EstadoDescarga
import es.rboluda.klippo.sesion.Plataforma

// Tarjeta de una descarga: título (o URL), estado y barra de progreso
@Composable
fun FilaDescarga(
    descarga: Descarga,
    actualizandoMotor: Boolean,
    onCancelar: () -> Unit,
    onActualizarYReintentar: () -> Unit,
    onIniciarSesion: (Plataforma) -> Unit,
    onAbrir: () -> Unit,
) {
    // El error completo de yt-dlp es muy largo: se muestra resumido y se expande al tocar
    var errorCompleto by remember { mutableStateOf(false) }

    // Completada: abre Exportar. Fallida: muestra/oculta el error completo
    val tocable = when (descarga.estado) {
        EstadoDescarga.Completada -> Modifier.clickable(onClick = onAbrir)
        is EstadoDescarga.Fallida -> Modifier.clickable { errorCompleto = !errorCompleto }
        else -> Modifier
    }
    Card(
        modifier = Modifier.fillMaxWidth().then(tocable),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniaturaVideo(
                archivo = descarga.miniatura,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = descarga.titulo ?: descarga.url,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (descarga.estado == EstadoDescarga.Descargando) {
                    if (descarga.progreso < 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { descarga.progreso },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = textoEstado(descarga, errorCompleto),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (descarga.estado is EstadoDescarga.Fallida) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (descarga.estado == EstadoDescarga.Descargando) {
                        TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
                    }
                }
                if (descarga.estado == EstadoDescarga.Completada) {
                    AccionesRapidas(
                        descarga = descarga,
                        onEditar = onAbrir,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
                val estado = descarga.estado
                if (estado is EstadoDescarga.Fallida) {
                    // Si yt-dlp pide sesión y la plataforma la admite, se ofrece iniciarla
                    val plataforma = Plataforma.deUrl(descarga.url)
                    if (plataforma != null && pideSesion(estado.mensaje)) {
                        TextButton(
                            onClick = { onIniciarSesion(plataforma) },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.sesion_iniciar, plataforma.nombre))
                        }
                    }
                    // Las plataformas cambian a menudo: lo primero ante un fallo es actualizar yt-dlp
                    TextButton(
                        onClick = onActualizarYReintentar,
                        enabled = !actualizandoMotor,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.descarga_actualizar_reintentar))
                    }
                }
            }
        }
    }
}

// Ficha del modo mosaico (solo para descargas completadas): miniatura y título
@Composable
fun FichaMosaico(descarga: Descarga, onAbrir: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAbrir),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        MiniaturaVideo(
            archivo = descarga.miniatura,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .padding(6.dp)
                .clip(RoundedCornerShape(18.dp)),
        )
        Text(
            text = descarga.titulo ?: descarga.url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        )
        AccionesRapidas(
            descarga = descarga,
            onEditar = onAbrir,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun textoEstado(descarga: Descarga, errorCompleto: Boolean): String = when (val estado = descarga.estado) {
    EstadoDescarga.Descargando -> {
        val porcentaje = (descarga.progreso * 100).toInt()
        when {
            descarga.enCola -> stringResource(R.string.descarga_en_cola)
            descarga.progreso < 0f -> stringResource(R.string.descarga_analizando)
            // Lista: porcentaje del total y el elemento que se está bajando ("40 % · 4 de 10")
            descarga.totalElementos > 1 -> stringResource(
                R.string.descarga_progreso_lista,
                porcentaje,
                descarga.elemento,
                descarga.totalElementos,
            )
            descarga.etaSegundos > 0 -> stringResource(
                R.string.descarga_progreso_eta,
                porcentaje,
                formatearDuracion(descarga.etaSegundos),
            )
            else -> stringResource(R.string.descarga_progreso, porcentaje)
        }
    }
    EstadoDescarga.Completada -> stringResource(R.string.descarga_completada)
    EstadoDescarga.Cancelada -> stringResource(R.string.descarga_cancelada)
    is EstadoDescarga.Fallida -> stringResource(
        R.string.descarga_fallida,
        if (errorCompleto) estado.mensaje else resumirError(estado.mensaje),
    )
}

// "[Instagram] ABC123: Instagram sent an empty media response. Check if..." →
// "Instagram sent an empty media response."
private val PREFIJO_EXTRACTOR = Regex("""^\[[^\]]+]\s*[^:\s]+:\s*""")

private fun resumirError(mensaje: String): String {
    val sinPrefijo = mensaje.replace(PREFIJO_EXTRACTOR, "")
    val fin = sinPrefijo.indexOf(". ")
    return if (fin > 0) sinPrefijo.substring(0, fin + 1) else sinPrefijo
}

private fun pideSesion(mensaje: String): Boolean =
    listOf("cookies", "login", "logged-in", "log in").any { mensaje.contains(it, ignoreCase = true) }

private fun formatearDuracion(segundos: Long): String =
    "%d:%02d".format(segundos / 60, segundos % 60)
