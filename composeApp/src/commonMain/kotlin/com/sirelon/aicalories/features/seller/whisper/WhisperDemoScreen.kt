package com.sirelon.sellsnap.features.seller.whisper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohamedrejeb.calf.permissions.Permission
import com.mohamedrejeb.calf.permissions.RecordAudio
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.features.media.PermissionDialogs
import com.sirelon.sellsnap.features.media.rememberPermissionController
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WhisperDemoScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WhisperViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionController = rememberPermissionController(permission = Permission.RecordAudio)

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is WhisperContract.Effect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    WhisperDemoScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRecordClick = {
            permissionController.requestPermission {
                viewModel.onEvent(WhisperContract.Event.RecordToggled)
            }
        },
        onLiveRecordClick = {
            permissionController.requestPermission {
                viewModel.onEvent(WhisperContract.Event.LiveRecordToggled)
            }
        },
        onTranscribeClick = {
            viewModel.onEvent(WhisperContract.Event.TranscribeClicked)
        },
        modifier = modifier,
    )

    PermissionDialogs(controller = permissionController)
}

@Composable
private fun WhisperDemoScreen(
    state: WhisperContract.State,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRecordClick: () -> Unit,
    onLiveRecordClick: () -> Unit,
    onTranscribeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Whisper PoC") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .padding(padding)
                .padding(AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRecordClick,
                enabled = !state.isTranscribing && !state.isLiveTranscribing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) {
                        AppTheme.colors.error
                    } else {
                        AppTheme.colors.primary
                    },
                ),
            ) {
                Text(if (state.isRecording) "Stop Recording" else "Start Recording")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLiveRecordClick,
                enabled = !state.isRecording && !state.isTranscribing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isLiveTranscribing) {
                        AppTheme.colors.error
                    } else {
                        AppTheme.colors.primary
                    },
                ),
            ) {
                Text(if (state.isLiveTranscribing) "Stop Live Text" else "Start Live Text")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onTranscribeClick,
                enabled = state.hasAudioData &&
                        !state.isRecording &&
                        !state.isTranscribing &&
                        !state.isLiveTranscribing,
            ) {
                Text("Transcribe")
            }

            if (state.isTranscribing) {
                CircularProgressIndicator()
            }

            if (state.transcriptionText.isNotBlank()) {
                Text(
                    text = state.transcriptionText,
                    color = AppTheme.colors.onSurface,
                    fontSize = AppDimens.TextSize.xl3,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (state.liveTranscriptionText.isNotBlank()) {
                Text(
                    text = state.liveTranscriptionText,
                    color = AppTheme.colors.onSurface,
                    fontSize = AppDimens.TextSize.xl3,
                    fontWeight = FontWeight.Medium,
                )
            }

            state.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = AppTheme.colors.error,
                    fontSize = AppDimens.TextSize.xl2,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
