package es.rboluda.klippo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.rboluda.klippo.edicion.ParametrosEdicion
import es.rboluda.klippo.ui.ajustes.AcercaDeScreen
import es.rboluda.klippo.ui.ajustes.AjustesScreen
import es.rboluda.klippo.ui.editor.EditorScreen
import es.rboluda.klippo.ui.editor.EditorViewModel
import es.rboluda.klippo.ui.exportar.ExportarScreen
import es.rboluda.klippo.ui.inicio.InicioScreen
import es.rboluda.klippo.ui.inicio.InicioViewModel
import es.rboluda.klippo.ui.spotify.SpotifyScreen
import es.rboluda.klippo.ui.spotify.SpotifyViewModel
import android.net.Uri

// Pantallas principales: Inicio → Editor → Exportar; y Ajustes → Acerca de
object Rutas {
    const val INICIO = "inicio"
    const val AJUSTES = "ajustes"
    const val ACERCA_DE = "acerca"
    const val ARG_ID = "id"
    const val EDITOR = "editor/{$ARG_ID}"
    const val EXPORTAR = "exportar/{$ARG_ID}"

    const val SPOTIFY = "spotify?${SpotifyViewModel.ARG_URL}={${SpotifyViewModel.ARG_URL}}"

    fun spotify(url: String) = "spotify?${SpotifyViewModel.ARG_URL}=${Uri.encode(url)}"
    fun editor(id: String) = "editor/$id"
    fun exportar(id: String) = "exportar/$id"
}

private val argumentoId = listOf(navArgument(Rutas.ARG_ID) { type = NavType.StringType })

@Composable
fun KlippoNavegacion(inicioViewModel: InicioViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) {
            InicioScreen(
                viewModel = inicioViewModel,
                onAbrir = { id ->
                    nav.navigate(if (inicioViewModel.esAudio(id)) Rutas.exportar(id) else Rutas.editor(id))
                },
                onAjustes = { nav.navigate(Rutas.AJUSTES) },
                onSpotify = { url -> nav.navigate(Rutas.spotify(url)) },
            )
        }
        composable(
            Rutas.SPOTIFY,
            arguments = listOf(navArgument(SpotifyViewModel.ARG_URL) { type = NavType.StringType; defaultValue = "" }),
        ) {
            SpotifyScreen(onVolver = { nav.popBackStack() })
        }
        composable(Rutas.AJUSTES) {
            AjustesScreen(
                onVolver = { nav.popBackStack() },
                onAcercaDe = { nav.navigate(Rutas.ACERCA_DE) },
            )
        }
        composable(Rutas.ACERCA_DE) {
            AcercaDeScreen(onVolver = { nav.popBackStack() })
        }
        composable(Rutas.EDITOR, arguments = argumentoId) {
            EditorScreen(
                onVolver = { nav.popBackStack() },
                onSiguiente = { id -> nav.navigate(Rutas.exportar(id)) },
            )
        }
        composable(Rutas.EXPORTAR, arguments = argumentoId) { entrada ->
            // Los parámetros de edición viven en el ViewModel del editor (que sigue debajo en
            // la pila y los guarda en SavedStateHandle): así no hay que pasarlos por la ruta.
            // Los audios llegan sin pasar por el editor: se exportan tal cual.
            val entradaEditor = remember(entrada) {
                runCatching { nav.getBackStackEntry(Rutas.EDITOR) }.getOrNull()
            }
            val parametros = if (entradaEditor != null) {
                val editor: EditorViewModel = viewModel(entradaEditor, factory = EditorViewModel.Factory)
                remember(entrada) { editor.parametros() }
            } else {
                ParametrosEdicion()
            }
            ExportarScreen(parametros = parametros, onVolver = { nav.popBackStack() })
        }
    }
}
