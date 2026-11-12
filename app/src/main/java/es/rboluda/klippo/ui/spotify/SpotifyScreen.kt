package es.rboluda.klippo.ui.spotify

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.rboluda.klippo.R
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.EstadoDescarga
import es.rboluda.klippo.descarga.PermisoNotificaciones
import es.rboluda.klippo.spotify.Similitud
import es.rboluda.klippo.ui.inicio.MiniaturaVideo

// Colores de las etiquetas de similitud (el rojo es el de error del tema)
private val VERDE = Color(0xFF2E9E4F)
private val AMARILLO = Color(0xFFC98A00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyScreen(
    onVolver: () -> Unit,
    viewModel: SpotifyViewModel = viewModel(factory = SpotifyViewModel.Factory),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val filas by viewModel.filas.collectAsStateWithLifecycle()
    val descargas by viewModel.descargas.collectAsStateWithLifecycle()
    val mp3 by viewModel.mp3.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Acción que espera a la respuesta del permiso de notificaciones (se lanza igualmente)
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendiente?.invoke()
        pendiente = null
    }
    val conPermiso = { accion: () -> Unit ->
        if (viewModel.debePedirPermisoNotificaciones()) {
            viewModel.marcarPermisoPedido()
            pendiente = accion
            pedirPermiso.launch(PermisoNotificaciones.PERMISO)
        } else {
            accion()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spotify_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(painterResource(R.drawable.ic_atras), stringResource(R.string.volver))
                    }
                },
            )
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when (val e = estado) {
                EstadoSpotify.Cargando -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.spotify_leyendo), Modifier.padding(top = 16.dp))
                }
                is EstadoSpotify.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.spotify_error, e.mensaje),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::cargar) { Text(stringResource(R.string.reintentar)) }
                }
                is EstadoSpotify.Lista -> {
                    val buscadas = filas.count { it.coincidencia != null }
                    val identicas = filas.count { it.coincidencia?.similitud == Similitud.IDENTICA }
                    val parecidas = filas.count { it.coincidencia?.similitud == Similitud.PARECIDA }
                    val noEncontradas = filas.count { it.coincidencia?.similitud == Similitud.NO_ENCONTRADA }
                    val descargables = filas.count { it.descargable(descargas) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item {
                            Cabecera(e, formato = if (mp3) "MP3" else "M4A")
                        }
                        if (e.lista.truncada) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.spotify_truncada, e.lista.pistas.size, e.lista.totalReal ?: 0),
                                        Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        item {
                            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (buscadas < filas.size) {
                                    Text(
                                        stringResource(R.string.spotify_buscando, buscadas, filas.size),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    LinearProgressIndicator(
                                        progress = { buscadas.toFloat() / filas.size.coerceAtLeast(1) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.spotify_resumen, identicas, parecidas, noEncontradas),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = {
                                        conPermiso {
                                            viewModel.descargarTodas()
                                            Toast.makeText(context, R.string.spotify_descargando_aviso, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = buscadas == filas.size && descargables > 0,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ic_guardar_galeria), null, Modifier.size(20.dp))
                                    Text(
                                        pluralStringResource(R.plurals.spotify_descargar_todas, descargables, descargables),
                                        Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                        items(filas, key = { it.pista.id + it.pista.numero }) { fila ->
                            FilaCancion(
                                fila = fila,
                                descarga = fila.idDescarga?.let { descargas[it] },
                                descargable = fila.descargable(descargas),
                                onDescargar = { conPermiso { viewModel.descargar(fila.pista.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cabecera(estado: EstadoSpotify.Lista, formato: String) {
    val lista = estado.lista
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniaturaVideo(
            archivo = estado.portada,
            modifier = Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lista.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            lista.autor?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                pluralStringResource(R.plurals.lista_canciones, lista.pistas.size, lista.pistas.size) + " · $formato",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Una canción: número, título e intérprete de Spotify, lo encontrado en YouTube, la etiqueta
// de similitud y su descarga. Tocarla abre el vídeo en YouTube para comprobarlo.
@Composable
private fun FilaCancion(fila: FilaPista, descarga: Descarga?, descargable: Boolean, onDescargar: () -> Unit) {
    val context = LocalContext.current
    val candidato = fila.coincidencia?.candidato
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = candidato != null) {
                candidato?.let { context.startActivity(Intent(Intent.ACTION_VIEW, it.url.toUri())) }
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${fila.pista.numero}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                fila.pista.titulo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fila.pista.artistas.joinToString(", ") + " · " + duracion(fila.pista.duracionMs / 1000),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (candidato != null) {
                Text(
                    "▶ " + listOfNotNull(
                        candidato.titulo,
                        candidato.canal,
                        candidato.duracionSeg?.toLong()?.let(::duracion),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EtiquetaSimilitud(fila.coincidencia?.similitud)
        }
        EstadoDescargaCancion(descarga, descargable, onDescargar)
    }
}

@Composable
private fun EtiquetaSimilitud(similitud: Similitud?) {
    val (texto, color) = when (similitud) {
        null -> return Text(
            stringResource(R.string.spotify_buscando_una),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Similitud.IDENTICA -> stringResource(R.string.spotify_identica) to VERDE
        Similitud.PARECIDA -> stringResource(R.string.spotify_parecida) to AMARILLO
        Similitud.NO_ENCONTRADA -> stringResource(R.string.spotify_no_encontrada) to MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
        Text(
            "● $texto",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// Botón de descarga o, si ya se ha pedido, su estado (en cola, avance, hecha)
@Composable
private fun EstadoDescargaCancion(descarga: Descarga?, descargable: Boolean, onDescargar: () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            descarga?.estado == EstadoDescarga.Completada -> Icon(
                painterResource(R.drawable.ic_hecho),
                stringResource(R.string.descarga_completada),
                tint = VERDE,
            )
            descarga?.estado == EstadoDescarga.Descargando -> {
                if (descarga.enCola || descarga.progreso < 0f) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    CircularProgressIndicator(progress = { descarga.progreso }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            descargable -> IconButton(onClick = onDescargar) {
                Icon(painterResource(R.drawable.ic_guardar_galeria), stringResource(R.string.inicio_descargar))
            }
        }
    }
}

private fun duracion(segundos: Long): String = "%d:%02d".format(segundos / 60, segundos % 60)
