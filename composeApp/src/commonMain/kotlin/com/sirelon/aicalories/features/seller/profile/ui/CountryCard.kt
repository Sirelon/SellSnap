package com.sirelon.sellsnap.features.seller.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.presentation.CountryRow
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.change_button
import com.sirelon.sellsnap.generated.resources.olx_country_picker_title
import com.sirelon.sellsnap.generated.resources.profile_country_subtitle
import com.sirelon.sellsnap.generated.resources.profile_country_title
import org.jetbrains.compose.resources.stringResource

/**
 * SIR-127: the guest's OLX market, which decides the language listings are written in and the
 * currency they're priced in. Same layout as `LocationCard`. "Change" opens the login picker's
 * country list in a sheet, and a tap saves it right away - no OLX login involved.
 */
@Composable
internal fun CountryCard(
    country: OlxCountry,
    onCountrySelected: (OlxCountry) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth().testTag("profile_country_card")) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
            ) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.Size.xl11)
                        .clip(CircleShape)
                        .background(AppTheme.colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = country.flag, fontSize = 22.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.profile_country_title),
                        style = AppTheme.typography.title,
                    )
                    Text(
                        text = stringResource(Res.string.profile_country_subtitle),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.onSurfaceMuted,
                    )
                }
            }

            Text(
                text = "${country.flag} ${country.nameEn}",
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurface,
            )

            AppButton(
                text = stringResource(Res.string.change_button),
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
                style = AppButtonDefaults.outline(),
            )
        }
    }

    if (showPicker) {
        CountryPickerSheet(
            selected = country,
            onSelect = { picked ->
                showPicker = false
                onCountrySelected(picked)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(
    selected: OlxCountry,
    onSelect: (OlxCountry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Spacing.xl4)
                .padding(bottom = AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Text(
                text = stringResource(Res.string.olx_country_picker_title),
                style = AppTheme.typography.headline,
                color = AppTheme.colors.onBackground,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimens.BorderRadius.xl3))
                    .border(
                        width = 1.dp,
                        color = AppTheme.colors.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(AppDimens.BorderRadius.xl3),
                    ),
            ) {
                OlxCountry.all.forEach { country ->
                    CountryRow(
                        country = country,
                        isSelected = country == selected,
                        isLast = country == OlxCountry.all.last(),
                        onClick = { onSelect(country) },
                        modifier = Modifier.testTag("profile_country_row_${country.code}"),
                    )
                }
            }
        }
    }
}
