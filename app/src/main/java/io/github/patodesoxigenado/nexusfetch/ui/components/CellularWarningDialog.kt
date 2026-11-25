package io.github.patodesoxigenado.nexusfetch.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun CellularWarningDialog(
    wifiOnlyBlocked: Boolean,
    onProceedAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (wifiOnlyBlocked) "Modo Apenas Wi-Fi Ativo" else "Uso de Dados Móveis (4G/5G)",
            )
        },
        text = {
            Text(
                text = if (wifiOnlyBlocked) {
                    "Você está conectado pela rede móvel e a opção \"Apenas Wi-Fi\" está ativada nas " +
                        "Configurações. Conecte-se a uma rede Wi-Fi para baixar arquivos remotos."
                } else {
                    "Você está em rede móvel (4G/5G). Deseja continuar este download ou recorte remoto mesmo assim?"
                },
            )
        },
        confirmButton = {
            if (wifiOnlyBlocked) {
                Button(onClick = onDismiss) {
                    Text("Entendi")
                }
            } else {
                Button(onClick = onProceedAnyway) {
                    Text("Continuar no 4G/5G")
                }
            }
        },
        dismissButton = {
            if (!wifiOnlyBlocked) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        },
    )
}
