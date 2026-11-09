package es.rboluda.klippo.ui.inicio

import android.Manifest
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as itemsGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.rboluda.klippo.BuildConfig
import es.rboluda.klippo.R
import es.rboluda.klippo.ui.comun.DialogoCalidad
import es.rboluda.klippo.ui.comun.LogoKlippo
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import es.rboluda.klippo.actualizacion.EstadoActualizacion
import es.rboluda.klippo.actualizacion.VersionNueva
import es.rboluda.klippo.ajustes.Calidad
import es.rboluda.klippo.ajustes.Vista
import es.rboluda.klippo.descarga.Descarga
import es.rboluda.klippo.descarga.EstadoDescarga
import es.rboluda.klippo.util.Enlaces
import es.rboluda.klippo.descarga.EstadoMotor
import es.rboluda.klippo.sesion.Plataforma
import es.rboluda.klippo.sesion.SesionActivity

// Pantalla de inicio: campo de URL y lista de descargas
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(
    viewModel: InicioViewModel = viewModel(factory = InicioViewModel.Factory),
    // Abre el editor de una descarga completada
    onAbrir: (id: String) -> Unit = {},
    onAjustes: () -> Unit = {},
) {
    val estadoMotor by viewModel.estadoMotor.collectAsStateWithLifecycle()
    val actualizando by viewModel.actualizando.collectAsStateWithLifecycle()
    val descargas by viewModel.descargas.collectAsStateWithLifecycle()
    val diasMotor by viewModel.diasMotor.collectAsStateWithLifecycle()
    val vista by viewModel.vista.collectAsStateWithLifecycle()
    val actualizacion by viewModel.actualizacion.collectAsStateWithLifecycle()
    val guardandoListas by viewModel.guardandoListas.collectAsStateWithLifecycle()
    // Listas desplegadas (por grupo); plegadas al abrir la app
    var desplegadas by rememberSaveable { mutableStateOf(listOf<String>()) }
    var url by rememberSaveable { mutableStateOf("") }
    var urlPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val iniciarSesion = { p: Plataforma -> context.startActivity(SesionActivity.intent(context, p)) }

    val ultimaCalidad by viewModel.ultimaCalidad.collectAsStateWithLifecycle()
    val preguntarCalidad by viewModel.preguntarCalidad.collectAsStateWithLifecycle()
    val urlCompartida by viewModel.urlCompartida.collectAsStateWithLifecycle()
    // Enlace esperando a que el usuario elija la calidad (diálogo abierto)
    var urlParaElegir by rememberSaveable { mutableStateOf<String?>(null) }
    var calidadPendiente by rememberSaveable { mutableStateOf<Calidad?>(null) }

    // Tras responder al permiso (sí o no) se lanza la descarga igualmente
    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val enlace = urlPendiente
        val calidadElegida = calidadPendiente
        if (enlace != null && calidadElegida != null) viewModel.descargar(enlace, calidadElegida)
        urlPendiente = null
        calidadPendiente = null
    }

    // El permiso de notificaciones se pide en la primera descarga, venga de donde venga
    val lanzarDescarga = { enlace: String, calidadElegida: Calidad ->
        if (viewModel.debePedirPermisoNotificaciones()) {
            viewModel.marcarPermisoPedido()
            urlPendiente = enlace
            calidadPendiente = calidadElegida
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.descargar(enlace, calidadElegida)
        }
    }

    // Pregunta la calidad o, si está desactivado en Ajustes, descarga con la predeterminada
    val prepararDescarga = { enlace: String ->
        if (preguntarCalidad) urlParaElegir = enlace else lanzarDescarga(enlace, ultimaCalidad)
    }

    val empezarDescarga = {
        if (url.isNotBlank() && estadoMotor is EstadoMotor.Listo) {
            prepararDescarga(url.trim())
            url = ""
        }
    }

    // Enlace compartido con "Klippo" desde otra app: se descarga y, al terminar, se abre
    // el editor (el servicio espera a que el motor esté listo si aún está arrancando)
    LaunchedEffect(urlCompartida) {
        urlCompartida?.let {
            viewModel.abrirEditorAlTerminar(it)
            prepararDescarga(it)
            viewModel.compartidoConsumido()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.abrirEditor.collect { onAbrir(it) }
    }

    urlParaElegir?.let { enlace ->
        DialogoCalidad(
            preseleccionada = ultimaCalidad,
            onElegir = { calidadElegida ->
                urlParaElegir = null
                lanzarDescarga(enlace, calidadElegida)
            },
            onCancelar = {
                urlParaElegir = null
                // Si venía del campo, se devuelve para no perderlo
                if (url.isBlank()) url = enlace
            },
        )
    }

    // Autorrelleno desde el portapapeles. Android solo deja leerlo con la ventana
    // enfocada, así que se hace al ganar el foco (al abrir o volver a la app)
    val ventanaEnfocada = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(ventanaEnfocada) {
        if (!ventanaEnfocada || url.isNotBlank()) return@LaunchedEffect
        val portapapeles = context.getSystemService(ClipboardManager::class.java)
        val texto = portapapeles.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
        val enlace = Enlaces.extraerUrl(texto)
        if (enlace != null && Enlaces.esSoportada(enlace) && viewModel.debeAutorrellenar(enlace)) {
            url = enlace
        }
    }

    LaunchedEffect(Unit) {
        viewModel.avisos.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoKlippo(tamano = 36.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            // Versión a la vista para saber qué compilación está instalada
                            Text(
                                text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    // El icono muestra la vista a la que se cambia
                    IconButton(onClick = viewModel::alternarVista) {
                        if (vista == Vista.LISTA) {
                            Icon(
                                painterResource(R.drawable.ic_vista_mosaico),
                                stringResource(R.string.vista_mosaico),
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_vista_lista),
                                stringResource(R.string.vista_lista),
                            )
                        }
                    }
                    IconButton(onClick = onAjustes) {
                        Icon(painterResource(R.drawable.ic_ajustes), stringResource(R.string.ajustes_titulo))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Barra fina mientras el motor arranca o se actualiza
            if (estadoMotor is EstadoMotor.Iniciando || actualizando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val dias = diasMotor
                if (dias != null && dias > InicioViewModel.DIAS_MOTOR_ANTIGUO) {
                    AvisoMotorAntiguo(dias, onActualizar = { viewModel.actualizarMotor() })
                }
            }
            AvisoActualizacion(actualizacion, onActualizar = viewModel::actualizarApp)
            // Campo del enlace: redondo, con pegar (vacío) o borrar (con texto) a la derecha
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.inicio_url_ayuda)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_enlace), null) },
                trailingIcon = {
                    if (url.isEmpty()) {
                        IconButton(onClick = {
                            val portapapeles = context.getSystemService(ClipboardManager::class.java)
                            Enlaces.extraerUrl(
                                portapapeles.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString()
                            )?.let { url = it }
                        }) {
                            Icon(painterResource(R.drawable.ic_pegar), stringResource(R.string.inicio_pegar))
                        }
                    } else {
                        IconButton(onClick = { url = "" }) {
                            Icon(painterResource(R.drawable.ic_cerrar), stringResource(R.string.inicio_borrar))
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { empezarDescarga() }),
            )
            Button(
                onClick = empezarDescarga,
                enabled = url.isNotBlank() && estadoMotor is EstadoMotor.Listo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_guardar_galeria), null, Modifier.size(20.dp))
                Text(stringResource(R.string.inicio_descargar), Modifier.padding(start = 8.dp))
            }

            if (descargas.isEmpty()) {
                PantallaVacia()
            } else {
                Text(
                    text = stringResource(R.string.inicio_tus_videos),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                val fila: @Composable (Descarga) -> Unit = { descarga ->
                    FilaDescarga(
                        descarga = descarga,
                        actualizandoMotor = actualizando,
                        onCancelar = { viewModel.cancelar(descarga.id) },
                        onActualizarYReintentar = { viewModel.actualizarMotor(reintentar = descarga) },
                        onIniciarSesion = iniciarSesion,
                        onAbrir = { onAbrir(descarga.id) },
                    )
                }
                // Las listas se muestran como una sola tarjeta; al desplegarla aparecen sus
                // vídeos debajo. "Elementos" es lo que se pinta: tarjetas de lista y descargas
                val entradas = remember(descargas) { agrupar(descargas) }
                val elementos = entradas.flatMap { entrada ->
                    when (entrada) {
                        is EntradaInicio.Unica -> listOf(entrada)
                        is EntradaInicio.Lista -> listOf(entrada) +
                            if (entrada.grupo in desplegadas) entrada.elementos.map { EntradaInicio.Unica(it) } else emptyList()
                    }
                }
                val tarjetaLista: @Composable (EntradaInicio.Lista) -> Unit = { lista ->
                    TarjetaLista(
                        lista = lista,
                        desplegada = lista.grupo in desplegadas,
                        guardados = guardandoListas[lista.grupo],
                        onDesplegar = {
                            desplegadas = if (lista.grupo in desplegadas) {
                                desplegadas - lista.grupo
                            } else {
                                desplegadas + lista.grupo
                            }
                        },
                        onGuardarTodo = { viewModel.guardarLista(lista.grupo) },
                    )
                }
                // Los vídeos de una lista desplegada van un poco metidos
                val sangria = { d: Descarga -> if (d.grupo != null) Modifier.padding(start = 16.dp) else Modifier }
                when (vista) {
                    Vista.LISTA -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(elementos, key = { it.clave }) { entrada ->
                            when (entrada) {
                                is EntradaInicio.Lista -> tarjetaLista(entrada)
                                is EntradaInicio.Unica -> Box(sangria(entrada.descarga)) { fila(entrada.descarga) }
                            }
                        }
                    }
                    // En mosaico, las descargas en curso o con error y las listas ocupan toda
                    // la fila (necesitan barra de progreso y botones); las completadas, una ficha
                    Vista.MOSAICO -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        itemsGrid(
                            elementos,
                            key = { it.clave },
                            span = { e ->
                                val ficha = e is EntradaInicio.Unica && e.descarga.estado == EstadoDescarga.Completada
                                GridItemSpan(if (ficha) 1 else maxLineSpan)
                            },
                        ) { entrada ->
                            when (entrada) {
                                is EntradaInicio.Lista -> tarjetaLista(entrada)
                                is EntradaInicio.Unica -> {
                                    val descarga = entrada.descarga
                                    if (descarga.estado == EstadoDescarga.Completada) {
                                        FichaMosaico(descarga, onAbrir = { onAbrir(descarga.id) })
                                    } else {
                                        fila(descarga)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Aviso de versión nueva de Klippo en GitHub, con su descarga y los errores
@Composable
private fun AvisoActualizacion(estado: EstadoActualizacion, onActualizar: (VersionNueva) -> Unit) {
    if (estado == EstadoActualizacion.Ninguna) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (estado) {
                is EstadoActualizacion.Disponible -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.actualizacion_disponible, estado.nueva.version),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onActualizar(estado.nueva) }) {
                        Text(stringResource(R.string.actualizar))
                    }
                }
                is EstadoActualizacion.Descargando -> {
                    Text(
                        stringResource(R.string.actualizacion_descargando, (estado.progreso * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(progress = { estado.progreso }, modifier = Modifier.fillMaxWidth())
                }
                is EstadoActualizacion.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.actualizacion_error, estado.mensaje),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onActualizar(estado.nueva) }) {
                        Text(stringResource(R.string.reintentar))
                    }
                }
                EstadoActualizacion.Ninguna -> Unit
            }
        }
    }
}

// Línea discreta cuando yt-dlp tiene más de un mes: las descargas empiezan a fallar
@Composable
private fun AvisoMotorAntiguo(dias: Int, onActualizar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.motor_antiguo, dias, dias),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onActualizar) { Text(stringResource(R.string.actualizar)) }
    }
}

// Sin descargas: el logo y una línea que explica cómo empezar
@Composable
private fun PantallaVacia() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LogoKlippo(tamano = 88.dp)
        Text(
            text = stringResource(R.string.inicio_lista_vacia),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.inicio_lista_vacia_detalle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
