package com.iblu01.portallauncher.ui.components

import android.os.Handler
import android.os.Looper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.transfer.ConfigSenderClient
import com.iblu01.portallauncher.transfer.ConfigTransferCandidate
import com.iblu01.portallauncher.transfer.ConfigTransferDiscovery
import kotlinx.coroutines.launch

private enum class NearbyTransferStatus { READY, SENDING, SENT, FAILED }

/** Apple-style proximity consent. Confidential bytes are created and sent only after approval. */
@Composable
fun ConfigTransferOverlay(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var candidate by remember { mutableStateOf<ConfigTransferCandidate?>(null) }
    var ignoredSession by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(NearbyTransferStatus.READY) }
    val discovery = remember { ConfigTransferDiscovery(context) }
    val sender = remember { ConfigSenderClient() }

    DisposableEffect(discovery) {
        discovery.start { found ->
            main.post {
                val next = found.firstOrNull { it.sessionId != ignoredSession }
                if (candidate == null && next != null) {
                    candidate = next
                    status = NearbyTransferStatus.READY
                }
            }
        }
        onDispose { discovery.stop() }
    }

    val target = candidate ?: return
    AlertDialog(
        onDismissRequest = {
            if (status != NearbyTransferStatus.SENDING) {
                ignoredSession = target.sessionId
                candidate = null
            }
        },
        title = {
            Text(
                stringResource(
                    if (status == NearbyTransferStatus.SENT) R.string.config_transfer_sent
                    else R.string.config_transfer_nearby_title,
                ),
            )
        },
        text = {
            when (status) {
                NearbyTransferStatus.READY -> Text(
                    stringResource(R.string.config_transfer_nearby_body, target.displayName),
                )
                NearbyTransferStatus.SENDING -> CircularProgressIndicator()
                NearbyTransferStatus.SENT -> Text(stringResource(R.string.config_transfer_received_body))
                NearbyTransferStatus.FAILED -> Text(stringResource(R.string.config_transfer_send_failed))
            }
        },
        confirmButton = {
            when (status) {
                NearbyTransferStatus.READY, NearbyTransferStatus.FAILED -> Button(
                    onClick = {
                        status = NearbyTransferStatus.SENDING
                        scope.launch {
                            val payload = prefs.exportTransferPayload()
                            val result = sender.handshake(target).fold(
                                onSuccess = { sender.sendPayload(it, payload) },
                                onFailure = { Result.failure(it) },
                            )
                            payload.fill(0)
                            status = if (result.isSuccess) NearbyTransferStatus.SENT
                            else NearbyTransferStatus.FAILED
                        }
                    },
                ) { Text(stringResource(R.string.config_transfer_approve)) }
                NearbyTransferStatus.SENT -> Button(onClick = { candidate = null }) {
                    Text(stringResource(android.R.string.ok))
                }
                NearbyTransferStatus.SENDING -> Text(stringResource(R.string.config_transfer_sending))
            }
        },
        dismissButton = {
            if (status == NearbyTransferStatus.READY || status == NearbyTransferStatus.FAILED) {
                TextButton(onClick = {
                    ignoredSession = target.sessionId
                    candidate = null
                }) { Text(stringResource(R.string.config_transfer_not_now)) }
            }
        },
    )
}
