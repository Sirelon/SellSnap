package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppDivider
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.IconWithBackground
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.buttons.AppButtonStyle
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.designsystem.templates.TermsAndPrivacy
import com.sirelon.sellsnap.designsystem.templates.TitleWithSubtitle
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.benefit_manage
import com.sirelon.sellsnap.generated.resources.benefit_publish
import com.sirelon.sellsnap.generated.resources.benefit_sync
import com.sirelon.sellsnap.generated.resources.continue_as_guest
import com.sirelon.sellsnap.generated.resources.continue_with_olx
import com.sirelon.sellsnap.generated.resources.guest_description
import com.sirelon.sellsnap.generated.resources.ic_check
import com.sirelon.sellsnap.generated.resources.ic_snap_logo
import com.sirelon.sellsnap.generated.resources.ic_user
import com.sirelon.sellsnap.generated.resources.img_seller_landing_welcome
import com.sirelon.sellsnap.generated.resources.or_divider
import com.sirelon.sellsnap.generated.resources.welcome_subtitle
import com.sirelon.sellsnap.generated.resources.welcome_to_sellsnap
import com.sirelon.sellsnap.generated.resources.why_connect_olx
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SellerLandingScreenRoute(openHome: () -> Unit) {
    val viewModel: SellerAuthViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    val localUriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is SellerAuthContract.SellerAuthEffect.LaunchOlxAuthFlow -> {
                webViewUrl = effect.url
            }

            is SellerAuthContract.SellerAuthEffect.ShowMessage -> {
                snackbarHostState.showSnackbar(effect.message)
            }

            is SellerAuthContract.SellerAuthEffect.LaunchBrowser -> {
                localUriHandler.openUri(effect.url)
            }

            SellerAuthContract.SellerAuthEffect.OpenHome -> openHome()
        }
    }

    LoadingOverlay(
        isLoading = state.status == SellerAuthContract.SellerAuthStatus.Processing,
        content = {
            SellerLandingScreen(state = state, onEvent = viewModel::onEvent)
        }
    )

    webViewUrl?.let { url ->
        Dialog(
            onDismissRequest = {
                webViewUrl = null
                viewModel.onEvent(SellerAuthContract.SellerAuthEvent.OlxAuthDismissed)
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            OlxAuthDialogScreen(
                url = url,
                onDismiss = {
                    webViewUrl = null
                    viewModel.onEvent(SellerAuthContract.SellerAuthEvent.OlxAuthDismissed)
                },
                onCallbackReceived = { callbackUrl ->
                    webViewUrl = null
                    viewModel.onCallbackReceived(callbackUrl)
                },
            )
        }
    }
}

@Composable
fun SellerLandingScreen(
    state: SellerAuthContract.SellerAuthState,
    onEvent: (SellerAuthContract.SellerAuthEvent) -> Unit
) {
    AppScaffold(
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppDimens.Spacing.xl6)
                .padding(top = AppDimens.Spacing.xl10),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl10)
        ) {
            SellerLandingHero()

            TitleWithSubtitle(
                title = stringResource(Res.string.welcome_to_sellsnap),
                subtitle = stringResource(Res.string.welcome_subtitle),
            )

            if (state.status == SellerAuthContract.SellerAuthStatus.Error) {
                Text(
                    modifier = Modifier.padding(top = AppDimens.Spacing.xl3).fillMaxWidth(),
                    text = state.errorMessage ?: "Something went wrong. Try again.",
                    color = AppTheme.colors.error,
                    style = AppTheme.typography.body,
                    textAlign = TextAlign.Center,
                )
            }

            ContinueWithOlxBlock(
                onContinueWithOlx = {
                    onEvent(SellerAuthContract.SellerAuthEvent.OlxAuthClicked)
                },
            )

            // Divider with "or"
            AppDivider(
                middleContent = {
                    Text(
                        text = stringResource(Res.string.or_divider),
                        modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                        style = AppTheme.typography.label,
                        color = AppTheme.colors.onSurfaceSoft
                    )
                },
            )

            ContinueAsGuestBlock(
                onContinueAsGuest = {
                    onEvent(SellerAuthContract.SellerAuthEvent.ContinueAsGuestClicked)
                },
            )

            TermsAndPrivacy(
                onTermsClick = {
                    onEvent(SellerAuthContract.SellerAuthEvent.OnTermsClicked)
                },
                onPrivacyClick = {
                    onEvent(SellerAuthContract.SellerAuthEvent.OnPrivacyClicked)
                }
            )
        }
    }
}

@Composable
private fun SellerLandingHero(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AppDimens.BorderRadius.xl11)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.Size.xl23)
            .clip(shape)
            .background(AppTheme.colors.surfaceLow),
    ) {
        Image(
            painter = painterResource(Res.drawable.img_seller_landing_welcome),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        IconWithBackground(
            backgroundColor = AppTheme.colors.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(AppDimens.Spacing.xl3)
                .size(AppDimens.Size.xl12),
            iconPadding = AppDimens.Spacing.xl3,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_snap_logo),
                contentDescription = null,
                tint = AppTheme.colors.onPrimary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ContinueAsGuestBlock(onContinueAsGuest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)) {
        // Continue as Guest Info
        ContinueAsGuestInfoBlock()
        AppButton(
            text = stringResource(Res.string.continue_as_guest),
            onClick = onContinueAsGuest,
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonDefaults.outline(),
            leadingIcon = painterResource(Res.drawable.ic_user)
        )
    }
}

@Composable
private fun ContinueWithOlxBlock(onContinueWithOlx: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)) {
        // Why connect to OLX card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.primary.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.Spacing.xl6),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)
            ) {
                Text(
                    text = stringResource(Res.string.why_connect_olx),
                    style = AppTheme.typography.title,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.onBackground
                )

                BenefitItem(text = stringResource(Res.string.benefit_publish))
                BenefitItem(text = stringResource(Res.string.benefit_sync))
                BenefitItem(text = stringResource(Res.string.benefit_manage))
            }
        }

        AppButton(
            text = stringResource(Res.string.continue_with_olx),
            onClick = onContinueWithOlx,
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonStyle(
                backgroundColor = AppTheme.colors.primary,
                contentColor = AppTheme.colors.onPrimary,
            ),
            leadingIcon = null
        )
    }
}

@Composable
private fun ContinueAsGuestInfoBlock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl)),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4)
    ) {
        Box(
            modifier = Modifier
                .size(AppDimens.Size.xl12)
                .clip(RoundedCornerShape(AppDimens.BorderRadius.m))
                .background(AppTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_user),
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceSoft
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs)) {
            Text(
                text = stringResource(Res.string.continue_as_guest),
                style = AppTheme.typography.title,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.onBackground
            )
            Text(
                text = stringResource(Res.string.guest_description),
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceSoft,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
            tint = AppTheme.colors.success,
            modifier = Modifier.size(AppDimens.Size.xl3)
        )
        Text(
            text = text,
            style = AppTheme.typography.body,
            color = AppTheme.colors.onBackground
        )
    }
}

@Preview
@Composable
private fun SellerLandingScreenPreview() {
    AppTheme {
        SellerLandingScreen(
            state = SellerAuthContract.SellerAuthState(),
            onEvent = {},
        )
    }
}
