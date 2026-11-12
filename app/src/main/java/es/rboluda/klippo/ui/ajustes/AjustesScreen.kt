package es.rboluda.klippo.ui.ajustes

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.rboluda.klippo.R
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.ajustes.Tema
import es.rboluda.klippo.ajustes.Vista
import es.rboluda.klippo.descarga.EstadoMotor
import es.rboluda.klippo.sesion.Plataforma
import es.rboluda.klippo.sesion.SesionActivity

// Todas las opciones de la app agrupadas por temas, y el acceso a "Acerca de"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    onVolver: () -> Unit,
    onAcercaDe: () -> Unit,
    viewModel: AjustesViewModel = viewModel(factory = AjustesViewModel.Factory),
) {
    val tema by viewModel.tema.collectAsStateWithLifecycle()
    val coloresSistema by viewModel.coloresSistema.collectAsStateWithLifecycle()
    val preguntarCalidad by viewModel.preguntarCalidad.collectAsStateWithLifecycle()
    val calidad by viewModel.calidad.collectAsStateWithLifecycle()
    val vista by viewModel.vista.collectAsStateWithLifecycle()
    val spotifyMp3 by viewModel.spotifyMp3.collectAsStateWithLifecycle()
    val buscarActualizaciones by viewModel.buscarActualizaciones.collectAsStateWithLifecycle()
    val estadoMotor by viewModel.estadoMotor.collectAsStateWithLifecycle()
    val actualizandoMotor by viewModel.actualizandoMotor.collectAsStateWithLifecycle()
    val sesiones by viewModel.sesiones.collectAsStateWithLifecycle()
    val bytesCache by viewModel.bytesCache.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Al volver de iniciar sesión o de los ajustes del sistema, se refresca lo que pudo cambiar
    LifecycleResumeEffect(Unit) {
        viewModel.refrescarSesiones()
        viewModel.calcularCache()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.avisos.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ajustes_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(painterResource(R.drawable.ic_atras), stringResource(R.string.volver))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = relleno.calculateTopPadding(),
                bottom = relleno.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { Seccion(R.string.ajustes_apariencia) }
            item {
                Opcion(titulo = stringResource(R.string.ajustes_tema)) {
                    Segmentos(Tema.entries, tema, { stringResource(it.etiqueta) }, viewModel::elegirTema)
                }
            }
            item {
                Interruptor(
                    titulo = stringResource(R.string.ajustes_colores_sistema),
                    detalle = stringResource(R.string.ajustes_colores_sistema_detalle),
                    activo = coloresSistema,
                    onCambiar = viewModel::cambiarColoresSistema,
                )
            }

            item { Seccion(R.string.ajustes_descargas) }
            item {
                Interruptor(
                    titulo = stringResource(R.string.ajustes_preguntar_calidad),
                    detalle = stringResource(R.string.ajustes_preguntar_calidad_detalle),
                    activo = preguntarCalidad,
                    onCambiar = viewModel::cambiarPreguntarCalidad,
                )
            }
            item {
                Opcion(
                    titulo = stringResource(
                        if (preguntarCalidad) R.string.ajustes_calidad_marcada else R.string.ajustes_calidad
                    ),
                ) {
                    Segmentos(Calidad.entries, calidad, { stringResource(it.etiquetaCorta) }, viewModel::elegirCalidad)
                }
            }
            item {
                // Las canciones de Spotify se bajan de YouTube solo como audio
                Opcion(titulo = stringResource(R.string.ajustes_spotify_formato)) {
                    Segmentos(
                        listOf(false, true),
                        spotifyMp3,
                        { stringResource(if (it) R.string.calidad_audio_mp3 else R.string.calidad_audio) },
                        viewModel::elegirSpotifyMp3,
                    )
                }
            }
            item {
                Opcion(titulo = stringResource(R.string.ajustes_vista)) {
                    Segmentos(
                        Vista.entries,
                        vista,
                        { stringResource(if (it == Vista.LISTA) R.string.ajustes_vista_lista else R.string.ajustes_vista_mosaico) },
                        viewModel::elegirVista,
                    )
                }
            }

            item { Seccion(R.string.ajustes_motor) }
            item {
                val version = (estadoMotor as? EstadoMotor.Listo)?.version
                Fila(
                    titulo = stringResource(R.string.motor_version, version ?: "…"),
                    detalle = stringResource(R.string.ajustes_motor_detalle),
                ) {
                    if (actualizandoMotor) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = viewModel::actualizarMotor,
                            enabled = estadoMotor is EstadoMotor.Listo,
                        ) { Text(stringResource(R.string.actualizar)) }
                    }
                }
            }

            item { Seccion(R.string.ajustes_cuentas) }
            for (plataforma in Plataforma.entries) {
                item(key = plataforma.name) {
                    val conSesion = plataforma in sesiones
                    Fila(
                        titulo = plataforma.nombre,
                        detalle = stringResource(
                            if (conSesion) R.string.ajustes_sesion_iniciada else R.string.ajustes_sin_sesion
                        ),
                    ) {
                        TextButton(
                            onClick = {
                                if (conSesion) viewModel.cerrarSesion(plataforma)
                                else context.startActivity(SesionActivity.intent(context, plataforma))
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (conSesion) R.string.ajustes_cerrar_sesion else R.string.ajustes_iniciar_sesion
                                )
                            )
                        }
                    }
                }
            }
            item { Nota(R.string.ajustes_cuentas_nota) }

            item { Seccion(R.string.ajustes_almacenamiento) }
            item {
                Fila(
                    titulo = stringResource(R.string.ajustes_cache),
                    detalle = stringResource(
                        R.string.ajustes_cache_detalle,
                        bytesCache?.let { Formatter.formatShortFileSize(context, it) } ?: "…",
                    ),
                ) {
                    TextButton(onClick = viewModel::vaciarCache) {
                        Text(stringResource(R.string.ajustes_vaciar))
                    }
                }
            }

            item { Seccion(R.string.ajustes_notificaciones) }
            item {
                Fila(
                    titulo = stringResource(R.string.ajustes_notificaciones_sistema),
                    detalle = stringResource(R.string.ajustes_notificaciones_detalle),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                )
            }

            if (viewModel.actualizacionesActivas) {
                item { Seccion(R.string.ajustes_actualizaciones) }
                item {
                    Interruptor(
                        titulo = stringResource(R.string.ajustes_buscar_auto),
                        detalle = stringResource(R.string.ajustes_buscar_auto_detalle),
                        activo = buscarActualizaciones,
                        onCambiar = viewModel::cambiarBuscarActualizaciones,
                    )
                }
                item {
                    Fila(
                        titulo = stringResource(R.string.actualizacion_buscar),
                        onClick = viewModel::buscarActualizacionesAhora,
                    )
                }
            }

            item { Seccion(R.string.ajustes_klippo) }
            item {
                Fila(
                    titulo = stringResource(R.string.acerca_titulo),
                    icono = R.drawable.ic_info,
                    onClick = onAcercaDe,
                )
            }
        }
    }
}

// --- Piezas de la lista de ajustes ---

@Composable
private fun Seccion(titulo: Int) {
    Text(
        text = stringResource(titulo),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Nota(texto: Int) {
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

private val coloresFila @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

// Fila con título, detalle opcional y un control a la derecha; entera tocable si hay onClick
@Composable
private fun Fila(
    titulo: String,
    detalle: String? = null,
    icono: Int? = null,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = detalle?.let { { Text(it) } },
        leadingContent = icono?.let { { Icon(painterResource(it), null) } },
        trailingContent = control,
        colors = coloresFila,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun Interruptor(titulo: String, detalle: String, activo: Boolean, onCambiar: (Boolean) -> Unit) {
    Fila(titulo = titulo, detalle = detalle, onClick = { onCambiar(!activo) }) {
        Switch(checked = activo, onCheckedChange = onCambiar)
    }
}

// Título y, debajo, un control que ocupa todo el ancho (botones segmentados)
@Composable
private fun Opcion(titulo: String, control: @Composable () -> Unit) {
    Surface(color = Color.Transparent) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) { control() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmentos(
    opciones: List<T>,
    seleccionada: T,
    etiqueta: @Composable (T) -> String,
    onElegir: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opciones.forEachIndexed { i, opcion ->
            SegmentedButton(
                selected = opcion == seleccionada,
                onClick = { onElegir(opcion) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = opciones.size),
                // Con muchas opciones (calidades) no cabe la marca de seleccionada
                icon = {
                    if (opciones.size <= 4) SegmentedButtonDefaults.Icon(active = opcion == seleccionada)
                },
                label = { Text(etiqueta(opcion), maxLines = 1) },
            )
        }
    }
}
