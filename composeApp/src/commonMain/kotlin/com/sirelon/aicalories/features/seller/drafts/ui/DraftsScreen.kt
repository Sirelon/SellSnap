package com.sirelon.sellsnap.features.seller.drafts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.drafts.presentation.DraftsContract
import com.sirelon.sellsnap.features.seller.drafts.presentation.DraftsViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.action_undo
import com.sirelon.sellsnap.generated.resources.back
import com.sirelon.sellsnap.generated.resources.drafts_empty
import com.sirelon.sellsnap.generated.resources.drafts_removed
import com.sirelon.sellsnap.generated.resources.drafts_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DraftsScreenRoute(
    onBack: () -> Unit,
    openPreview: (AdvertisementWithAttributes) -> Unit,
) {
    val viewModel: DraftsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(Res.string.drafts_removed)
    val undoLabel = stringResource(Res.string.action_undo)

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is DraftsContract.DraftsEffect.OpenPreview -> openPreview(effect.listing)

            // Launched apart from the effect collector: a snackbar with an action suspends until
            // it is dismissed, and a draft opened in the meantime must not wait for that.
            DraftsContract.DraftsEffect.DraftRemoved -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = removedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onEvent(DraftsContract.DraftsEvent.UndoRemove)
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.drafts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val drafts = state.drafts
        if (drafts == null) {
            // Not read yet - see DraftsState.drafts.
        } else if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.drafts_empty),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.onSurfaceMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("drafts_list")
                    .consumeWindowInsets(padding),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftCard(
                        draft = draft,
                        currency = state.currency,
                        onOpen = { viewModel.onEvent(DraftsContract.DraftsEvent.Open(draft)) },
                        onRemove = { viewModel.onEvent(DraftsContract.DraftsEvent.Remove(draft)) },
                        modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                    )
                }
            }
        }
    }
}
