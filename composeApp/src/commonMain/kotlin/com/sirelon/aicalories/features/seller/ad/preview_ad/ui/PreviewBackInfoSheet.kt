package com.sirelon.sellsnap.features.seller.ad.preview_ad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.preview_back_info_leave
import com.sirelon.sellsnap.generated.resources.preview_back_info_message
import com.sirelon.sellsnap.generated.resources.preview_back_info_stay
import com.sirelon.sellsnap.generated.resources.preview_back_info_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun PreviewBackInfoSheet(
    onStay: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        Text(
            text = stringResource(Res.string.preview_back_info_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(Res.string.preview_back_info_message),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonDefaults.secondary(),
            text = stringResource(Res.string.preview_back_info_stay),
            onClick = onStay,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonDefaults.outline(),
            text = stringResource(Res.string.preview_back_info_leave),
            onClick = onLeave,
        )
    }
}
