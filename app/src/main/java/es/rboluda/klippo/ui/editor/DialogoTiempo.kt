package es.rboluda.klippo.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.rboluda.klippo.R
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Escribir a mano un tiempo del recorte con precisión de décima: minutos y segundos en dos
 * campos numéricos (el teclado numérico del móvil no tiene ":"). Solo acepta valores dentro
 * de [minimoMs]..[maximoMs]; si no, muestra el motivo y no cierra.
 */
@Composable
fun DialogoTiempo(
    titulo: String,
    valorMs: Long,
    minimoMs: Long,
    maximoMs: Long,
    onAceptar: (ms: Long) -> Unit,
    onCancelar: () -> Unit,
) {
    val decimas = valorMs / 100
    var minutos by remember { mutableStateOf((decimas / 600).toString()) }
    var segundos by remember {
        mutableStateOf(String.format(Locale.getDefault(), "%.1f", (decimas % 600) / 10.0))
    }
    var error by remember { mutableStateOf<String?>(null) }
    val textoFormato = stringResource(R.string.tiempo_error_formato)
    val textoRango = stringResource(R.string.tiempo_error_rango, formatearTiempo(minimoMs), formatearTiempo(maximoMs))

    val aceptar = {
        val ms = leerTiempo(minutos, segundos)
        when {
            ms == null -> error = textoFormato
            ms !in minimoMs..maximoMs -> error = textoRango
            else -> onAceptar(ms)
        }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutos,
                        onValueChange = { minutos = it.filter(Char::isDigit).take(3); error = null },
                        label = { Text(stringResource(R.string.tiempo_minutos)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.width(88.dp),
                    )
                    Text(":", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = segundos,
                        onValueChange = { nuevo ->
                            segundos = nuevo.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
                            error = null
                        },
                        label = { Text(stringResource(R.string.tiempo_segundos)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.width(120.dp),
                    )
                }
                Text(
                    text = error ?: stringResource(R.string.tiempo_ayuda),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = aceptar) { Text(stringResource(R.string.aceptar)) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) } },
    )
}

// Minutos (vacío = 0) y segundos con décimas ("5,3" o "5.3") → milisegundos redondeados a la
// décima; null si no es un número válido
internal fun leerTiempo(minutos: String, segundos: String): Long? {
    val min = minutos.trim().ifEmpty { "0" }.toLongOrNull() ?: return null
    val seg = segundos.trim().replace(',', '.').ifEmpty { "0" }.toDoubleOrNull() ?: return null
    if (min < 0 || seg < 0) return null
    return ((min * 60 + seg) * 10).roundToLong() * 100
}

// "1:05.3" (minutos, segundos y décimas)
internal fun formatearTiempo(ms: Long): String {
    val decimas = ms / 100
    return String.format(Locale.ROOT, "%d:%02d.%d", decimas / 600, (decimas / 10) % 60, decimas % 10)
}
