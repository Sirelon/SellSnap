package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_arrow_left
import com.sirelon.sellsnap.generated.resources.olx_country_picker_continue
import com.sirelon.sellsnap.generated.resources.olx_country_picker_select_prompt
import com.sirelon.sellsnap.generated.resources.olx_country_picker_subtitle
import com.sirelon.sellsnap.generated.resources.olx_country_picker_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val OLX_DARK = Color(0xFF002F34)

@Composable
fun OlxCountryPickerScreenRoute(
    onBack: () -> Unit,
    openHome: () -> Unit,
) {
    val viewModel: SellerAuthViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val authLauncher = rememberOlxAuthLauncher()

    LaunchedEffect(viewModel) {
        OlxAuthCallbackBridge.callbacks.collect { callbackUrl ->
            viewModel.onCallbackReceived(callbackUrl)
        }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is SellerAuthContract.SellerAuthEffect.LaunchOlxAuthFlow -> authLauncher(effect.url)
            SellerAuthContract.SellerAuthEffect.OpenHome -> openHome()
            is SellerAuthContract.SellerAuthEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            else -> Unit
        }
    }

    LoadingOverlay(
        isLoading = state.status == SellerAuthContract.SellerAuthStatus.Processing,
    ) {
        OlxCountryPickerScreen(
            preselected = _currentOlxCountry,
            onBack = onBack,
            errorMessage = state.errorMessage,
            onConfirm = { country ->
                viewModel.onEvent(SellerAuthContract.SellerAuthEvent.CountryConfirmed(country))
            },
        )
    }
}

@Composable
private fun OlxCountryPickerScreen(
    preselected: OlxCountry,
    onBack: () -> Unit,
    errorMessage: String?,
    onConfirm: (OlxCountry) -> Unit,
) {
    var selected by remember(preselected) { mutableStateOf(preselected) }

    AppScaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.xl3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_left),
                        contentDescription = null,
                        tint = AppTheme.colors.onBackground,
                    )
                }
                Spacer(Modifier.weight(1f))
                OlxBadge()
            }

            // Header
            Column(
                modifier = Modifier.padding(
                    horizontal = AppDimens.Spacing.xl6,
                    vertical = AppDimens.Spacing.xl3,
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            ) {
                Text(
                    text = stringResource(Res.string.olx_country_picker_title),
                    style = AppTheme.typography.display,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.onBackground,
                )
                Text(
                    text = stringResource(Res.string.olx_country_picker_subtitle),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.onSurfaceSoft,
                )
            }

            // Country list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.Spacing.xl5)
                    .clip(RoundedCornerShape(AppDimens.BorderRadius.xl3))
                    .border(
                        width = 1.dp,
                        color = AppTheme.colors.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(AppDimens.BorderRadius.xl3),
                    ),
            ) {
                items(OlxCountry.all, key = { it.code }) { country ->
                    CountryRow(
                        country = country,
                        isSelected = country == selected,
                        isLast = country == OlxCountry.all.last(),
                        onClick = { selected = country },
                        modifier = Modifier.testTag("country_row_${country.code}"),
                    )
                }
            }

            // Error
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = AppTheme.colors.error,
                    style = AppTheme.typography.label,
                    modifier = Modifier
                        .padding(horizontal = AppDimens.Spacing.xl6)
                        .padding(top = AppDimens.Spacing.xl3),
                )
            }

            // Continue button
            AppButton(
                text = stringResource(Res.string.olx_country_picker_continue) + " · ${selected.domain}",
                onClick = { onConfirm(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Spacing.xl5)
                    .padding(top = AppDimens.Spacing.xl3, bottom = AppDimens.Spacing.xl10)
                    .testTag("country_picker_continue_button"),
                style = AppButtonDefaults.primary(),
            )
        }
    }
}

@Composable
private fun CountryRow(
    country: OlxCountry,
    isSelected: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) OLX_DARK.copy(alpha = 0.07f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Spacing.xl4, vertical = AppDimens.Spacing.xl2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            Text(
                text = country.flag,
                fontSize = 26.sp,
                modifier = Modifier.size(AppDimens.Size.xl9),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = country.nameEn,
                    style = AppTheme.typography.title,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurface,
                )
                if (country.nameNative != country.nameEn) {
                    Text(
                        text = country.nameNative,
                        style = AppTheme.typography.label,
                        color = AppTheme.colors.onSurfaceSoft,
                    )
                }
            }
            // Domain pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.BorderRadius.s))
                    .background(
                        if (isSelected) OLX_DARK else AppTheme.colors.surfaceLow,
                    )
                    .padding(horizontal = AppDimens.Spacing.m, vertical = AppDimens.Spacing.xs),
            ) {
                Text(
                    text = country.domain,
                    style = AppTheme.typography.label,
                    color = if (isSelected) Color.White else AppTheme.colors.onSurfaceSoft,
                    fontWeight = FontWeight.Medium,
                )
            }
            // Checkmark circle
            Box(
                modifier = Modifier
                    .size(AppDimens.Size.xl6)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) OLX_DARK else AppTheme.colors.outline.copy(alpha = 0.25f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Text(text = "✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl4),
                color = AppTheme.colors.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun OlxBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.BorderRadius.m))
            .background(OLX_DARK)
            .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.xs),
    ) {
        Text(
            text = "OLX",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            letterSpacing = (-0.3).sp,
        )
    }
}
