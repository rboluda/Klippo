package es.rboluda.klippo.ui.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import es.rboluda.klippo.R
import es.rboluda.klippo.edicion.Formato
import es.rboluda.klippo.edicion.Recorte
import kotlinx.coroutines.delay
import java.io.File

// Herramientas de la barra inferior
private enum class Herramienta(@param:StringRes val nombre: Int, @param:DrawableRes val icono: Int) {
    RECORTAR(R.string.editor_recortar, R.drawable.ic_recortar),
    ENCUADRE(R.string.editor_encuadre, R.drawable.ic_encuadre),
    TEXTO(R.string.editor_texto, R.drawable.ic_texto),
    EMOJI(R.string.editor_emoji, R.drawable.ic_emoji),
    PIXELAR(R.string.editor_pixelar, R.drawable.ic_pixelar),
}

// Recordatorio de los gestos y botón para añadir (emojis y zonas pixeladas)
@Composable
private fun PanelAnadir(@StringRes ayuda: Int, onAnadir: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onAnadir) { Text(stringResource(R.string.emoji_anadir)) }
        }
    }
}

// Formatos de encuadre como chips; "Original" quita el encuadre
@Composable
private fun PanelEncuadre(seleccionado: Formato?, onElegir: (Formato?) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = seleccionado == null,
                onClick = { onElegir(null) },
                label = { Text(stringResource(R.string.encuadre_original)) },
            )
            for (formato in Formato.entries) {
                FilterChip(
                    selected = seleccionado == formato,
                    onClick = { onElegir(formato) },
                    label = {
                        Text(
                            if (formato == Formato.LIBRE) stringResource(R.string.encuadre_libre)
                            else formato.etiqueta
                        )
                    },
                )
            }
        }
    }
}

// Editor: vídeo arriba, panel de la herramienta activa y barra de herramientas abajo.
// "Siguiente" lleva a Exportar con los parámetros elegidos.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onVolver: () -> Unit,
    onSiguiente: (id: String) -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val descarga by viewModel.descarga.collectAsStateWithLifecycle()
    val medidas by viewModel.medidas.collectAsStateWithLifecycle()
    val duracionMs = medidas?.duracionMs ?: 0L
    val aspecto = medidas?.aspecto ?: 0f
    val recorte by viewModel.recorte.collectAsStateWithLifecycle()
    val encuadre by viewModel.encuadre.collectAsStateWithLifecycle()
    val emojis by viewModel.emojis.collectAsStateWithLifecycle()
    val pixelados by viewModel.pixelados.collectAsStateWithLifecycle()
    var eligiendoEmoji by rememberSaveable { mutableStateOf(false) }
    var escribiendoTexto by rememberSaveable { mutableStateOf(false) }
    val silenciar by viewModel.silenciar.collectAsStateWithLifecycle()

    if (escribiendoTexto) {
        DialogoTexto(
            onAnadir = { texto, estilo ->
                viewModel.agregarTexto(texto, estilo)
                escribiendoTexto = false
            },
            onCancelar = { escribiendoTexto = false },
        )
    }

    if (eligiendoEmoji) {
        DialogoEmoji(
            onElegir = {
                viewModel.agregarEmoji(it)
                eligiendoEmoji = false
            },
            onCancelar = { eligiendoEmoji = false },
        )
    }
    var herramienta by rememberSaveable { mutableStateOf<Herramienta?>(null) }
    val archivo = descarga?.archivo?.takeIf { it.exists() }
    val reproductor = archivo?.let { rememberReproductor(it) }

    // Sin sonido también en la vista previa, para oír cómo quedará
    LaunchedEffect(reproductor, silenciar) {
        reproductor?.volume = if (silenciar) 0f else 1f
    }

    // Vista previa del recorte: la reproducción se queda dentro del tramo elegido, en bucle
    LaunchedEffect(reproductor, recorte) {
        val tramo = recorte ?: return@LaunchedEffect
        val player = reproductor ?: return@LaunchedEffect
        while (true) {
            // En pausa no se toca: al arrastrar el final se muestra ese fotograma
            val posicion = player.currentPosition
            if (player.isPlaying && (posicion >= tramo.finMs || posicion < tramo.inicioMs - MARGEN_MS)) {
                player.seekTo(tramo.inicioMs)
            }
            delay(50)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(painterResource(R.drawable.ic_atras), stringResource(R.string.volver))
                    }
                },
                actions = {
                    // Quitar el sonido del vídeo exportado
                    IconButton(onClick = viewModel::alternarSilencio, enabled = archivo != null) {
                        Icon(
                            painterResource(if (silenciar) R.drawable.ic_silencio else R.drawable.ic_volumen),
                            stringResource(if (silenciar) R.string.editor_activar_sonido else R.string.editor_silenciar),
                            tint = if (silenciar) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    TextButton(
                        onClick = {
                            reproductor?.pause()
                            descarga?.let { onSiguiente(it.id) }
                        },
                        enabled = archivo != null,
                    ) {
                        Text(stringResource(R.string.siguiente))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                val tramo = recorte
                if (herramienta == Herramienta.RECORTAR && tramo != null && duracionMs > 0) {
                    PanelRecorte(
                        recorte = tramo,
                        duracionMs = duracionMs,
                        onCambiar = { inicio, fin, movido ->
                            viewModel.cambiarRecorte(inicio, fin)
                            // Mientras se arrastra se ve el fotograma del extremo que se mueve
                            reproductor?.pause()
                            reproductor?.seekTo(movido)
                        },
                        onSoltar = { reproductor?.seekTo(tramo.inicioMs) },
                    )
                }
                if (herramienta == Herramienta.ENCUADRE) {
                    PanelEncuadre(
                        seleccionado = encuadre?.formato,
                        onElegir = viewModel::elegirFormato,
                    )
                }
                if (herramienta == Herramienta.TEXTO) {
                    PanelAnadir(R.string.texto_ayuda, onAnadir = { escribiendoTexto = true })
                }
                if (herramienta == Herramienta.EMOJI) {
                    PanelAnadir(R.string.emoji_ayuda, onAnadir = { eligiendoEmoji = true })
                }
                if (herramienta == Herramienta.PIXELAR) {
                    PanelAnadir(R.string.pixelar_ayuda, onAnadir = viewModel::agregarPixelado)
                }
                NavigationBar {
                    for (opcion in Herramienta.entries) {
                        NavigationBarItem(
                            selected = herramienta == opcion,
                            onClick = { herramienta = if (herramienta == opcion) null else opcion },
                            // Encuadre y emojis necesitan conocer las medidas del vídeo
                            enabled = archivo != null && (opcion == Herramienta.RECORTAR || aspecto > 0f),
                            icon = { Icon(painterResource(opcion.icono), null) },
                            label = { Text(stringResource(opcion.nombre)) },
                        )
                    }
                }
            }
        },
    ) { relleno ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (reproductor != null) {
                // La caja tiene la proporción del vídeo: así la capa de encuadre coincide
                // exactamente con la imagen (sin las bandas negras del reproductor)
                Box(if (aspecto > 0f) Modifier.aspectRatio(aspecto) else Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { PlayerView(it).apply { player = reproductor } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Mismo orden que al exportar: pixelado, emojis y, encima, el encuadre
                    if (pixelados.isNotEmpty() || herramienta == Herramienta.PIXELAR) {
                        CapaPixelado(
                            zonas = pixelados,
                            editable = herramienta == Herramienta.PIXELAR,
                            aspectoVideo = aspecto,
                            onCambiar = viewModel::cambiarPixelado,
                            onBorrar = viewModel::borrarPixelado,
                        )
                    }
                    val editandoEmojis = herramienta == Herramienta.EMOJI || herramienta == Herramienta.TEXTO
                    if (emojis.isNotEmpty() || editandoEmojis) {
                        CapaEmojis(
                            emojis = emojis,
                            editable = editandoEmojis,
                            onMover = viewModel::moverEmoji,
                            onEscalar = viewModel::escalarEmoji,
                            onBorrar = viewModel::borrarEmoji,
                        )
                    }
                    encuadre?.let {
                        CapaEncuadre(
                            encuadre = it,
                            editable = herramienta == Herramienta.ENCUADRE,
                            aspectoVideo = aspecto,
                            onCambiar = viewModel::cambiarEncuadre,
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.exportar_no_disponible), color = Color.White)
            }
        }
    }
}

// Margen para no saltar al inicio por redondeos al buscar justo en el borde del tramo
private const val MARGEN_MS = 250L

// Extremo del recorte que se está escribiendo a mano
private enum class Extremo { INICIO, FIN }

// Doble barra para elegir inicio y fin; los tiempos son botones para escribirlos a mano
// con precisión de décima
@Composable
private fun PanelRecorte(
    recorte: Recorte,
    duracionMs: Long,
    onCambiar: (inicioMs: Long, finMs: Long, movidoMs: Long) -> Unit,
    onSoltar: () -> Unit,
) {
    var editando by rememberSaveable { mutableStateOf<Extremo?>(null) }
    val minimo = EditorViewModel.DURACION_MINIMA_MS

    when (editando) {
        Extremo.INICIO -> DialogoTiempo(
            titulo = stringResource(R.string.tiempo_titulo_inicio),
            valorMs = recorte.inicioMs,
            minimoMs = 0,
            maximoMs = recorte.finMs - minimo,
            onAceptar = { ms ->
                onCambiar(ms, recorte.finMs, ms)
                editando = null
            },
            onCancelar = { editando = null },
        )
        Extremo.FIN -> DialogoTiempo(
            titulo = stringResource(R.string.tiempo_titulo_fin),
            valorMs = recorte.finMs,
            minimoMs = recorte.inicioMs + minimo,
            maximoMs = duracionMs,
            onAceptar = { ms ->
                onCambiar(recorte.inicioMs, ms, ms)
                editando = null
            },
            onCancelar = { editando = null },
        )
        null -> Unit
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { editando = Extremo.INICIO }) {
                    Text(stringResource(R.string.recorte_inicio, formatearTiempo(recorte.inicioMs)))
                }
                Text(
                    text = formatearTiempo(recorte.duracionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editando = Extremo.FIN }) {
                    Text(stringResource(R.string.recorte_fin, formatearTiempo(recorte.finMs)))
                }
            }
            RangeSlider(
                value = recorte.inicioMs.toFloat()..recorte.finMs.toFloat(),
                onValueChange = { rango ->
                    val inicio = rango.start.toLong()
                    val fin = rango.endInclusive.toLong()
                    val movido = if (inicio != recorte.inicioMs) inicio else fin
                    onCambiar(inicio, fin, movido)
                },
                onValueChangeFinished = onSoltar,
                valueRange = 0f..duracionMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ExoPlayer ligado al ciclo de vida de la pantalla: se pausa al salir y se libera al cerrar
@Composable
private fun rememberReproductor(archivo: File): ExoPlayer {
    val context = LocalContext.current
    val reproductor = remember(archivo) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(archivo.toURI().toString()))
            prepare()
        }
    }
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(reproductor, ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) reproductor.pause()
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            reproductor.release()
        }
    }
    return reproductor
}
