package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.onboarding.ConfigReceiveState
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState

/** Dedicated, non-interactive receiver experience entered as soon as the sender offers a session. */
@Composable
fun ReceiveConfigStep(
    state: OnboardingUiState,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val applied = state.configReceiveState == ConfigReceiveState.Applied
    val failed = state.configReceiveState == ConfigReceiveState.Failed
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!applied && !failed) CircularProgressIndicator()
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(
                when {
                    applied -> R.string.config_transfer_received_title
                    failed -> R.string.config_transfer_failed_title
                    else -> R.string.config_transfer_receiving_title
                },
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                when {
                    applied -> R.string.config_transfer_received_body
                    failed -> R.string.config_transfer_failed_body
                    else -> R.string.config_transfer_receiving_body
                },
            ),
            modifier = Modifier.widthIn(max = 520.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (applied) {
            Spacer(Modifier.height(28.dp))
            Button(onClick = onFinish) { Text(stringResource(R.string.config_transfer_open_launcher)) }
        }
    }
}
