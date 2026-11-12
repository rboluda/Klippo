package es.rboluda.klippo.ui.spotify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.rboluda.klippo.R
import es.rboluda.klippo.spotify.CandidatoYoutube
import kotlinx.coroutines.launch

/**
 * Búsqueda a mano de una canción: se puede cambiar el texto y elegir entre 10 resultados de
 * YouTube (cada uno con su parecido), o pegar directamente el enlace de un vídeo.
 */
@Composable
fun DialogoBusquedaManual(
    estado: BusquedaManual,
    onBuscar: (String) -> Unit,
    onElegir: (CandidatoYoutube) -> Unit,
    onUsarEnlace: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    var texto by rememberSaveable(estado.pista.id) { mutableStateOf(estado.consulta) }
    var enlace by rememberSaveable(estado.pista.id) { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val ambito = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Column {
                Text(stringResource(R.string.spotify_manual_titulo), style = MaterialTheme.typography.titleMedium)
                Text(
                    estado.pista.nombreCompleto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Buscar en YouTube con el texto que se quiera
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.spotify_manual_buscar)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onBuscar(texto) }),
                    trailingIcon = {
                        IconButton(onClick = { onBuscar(texto) }, enabled = !estado.buscando) {
                            Icon(painterResource(R.drawable.ic_buscar), stringResource(R.string.spotify_manual_buscar))
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 300.dp)) {
                    when {
                        estado.buscando -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
                        estado.buscado && estado.resultados.isEmpty() -> Text(
                            stringResource(R.string.spotify_manual_sin_resultados),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn {
                            items(estado.resultados, key = { it.first.id }) { (candidato, similitud) ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onElegir(candidato) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        candidato.titulo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(candidato.canal, candidato.duracionSeg?.toLong()?.let(::duracionTexto))
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    EtiquetaSimilitud(similitud)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                // 2. O pegar el enlace de un vídeo de YouTube
                OutlinedTextField(
                    value = enlace,
                    onValueChange = { enlace = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.spotify_manual_enlace)) },
                    placeholder = { Text("https://youtu.be/…") },
                    isError = estado.enlaceNoValido,
                    supportingText = if (estado.enlaceNoValido) {
                        { Text(stringResource(R.string.spotify_manual_enlace_no_valido)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onUsarEnlace(enlace) }),
                    trailingIcon = {
                        if (enlace.isEmpty()) {
                            // Pegar desde el portapapeles
                            IconButton(onClick = {
                                ambito.launch {
                                    clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)?.text?.toString()?.let { enlace = it.trim() }
                                }
                            }) {
                                Icon(painterResource(R.drawable.ic_pegar), stringResource(R.string.inicio_pegar))
                            }
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (estado.comprobandoEnlace) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.spotify_manual_comprobando),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUsarEnlace(enlace) },
                enabled = enlace.isNotBlank() && !estado.comprobandoEnlace,
            ) { Text(stringResource(R.string.spotify_manual_usar_enlace)) }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) }
        },
    )
}
