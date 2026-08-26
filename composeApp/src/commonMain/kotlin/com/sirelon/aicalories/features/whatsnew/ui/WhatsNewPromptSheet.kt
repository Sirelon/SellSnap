package com.sirelon.sellsnap.features.whatsnew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.designsystem.IconWithBackground
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.features.whatsnew.presentation.WhatsNewViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.whats_new_dialog_title
import com.sirelon.sellsnap.generated.resources.whats_new_got_it
import com.sirelon.sellsnap.generated.resources.whats_new_view_all
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WhatsNewPromptSheet(
    viewModel: WhatsNewViewModel,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val release = state.currentRelease ?: return

    // Swiping down, tapping the scrim, or pressing back all dismiss the sheet without going
    // through onDismiss/onViewAll below — mark seen on dispose so every dismissal path counts.
    DisposableEffect(Unit) {
        onDispose { viewModel.markSeen() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.Spacing.xl5)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        Text(
            text = stringResource(Res.string.whats_new_dialog_title, release.version),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )

        release.changes.forEach { change ->
            Cell(
                headline = {
                    Text(
                        text = change.title,
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurface,
                    )
                },
                supporting = {
                    Text(
                        text = change.summary,
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceMuted,
                    )
                },
                leading = {
                    IconWithBackground(
                        backgroundColor = AppTheme.colors.primary,
                        modifier = Modifier.size(AppDimens.Size.xl10),
                    ) {
                        Icon(
                            painter = painterResource(releaseChangeIcon(change.icon)),
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(AppDimens.Size.xl6),
                        )
                    }
                },
                transparent = true,
            )
        }

        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.whats_new_got_it),
            onClick = onDismiss,
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onViewAll) {
                Text(text = stringResource(Res.string.whats_new_view_all))
            }
        }
    }
}
