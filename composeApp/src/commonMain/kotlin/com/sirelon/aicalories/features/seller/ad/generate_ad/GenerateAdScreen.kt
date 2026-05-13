package com.sirelon.sellsnap.features.seller.ad.generate_ad

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.permissions.Camera
import com.mohamedrejeb.calf.permissions.Permission
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.IconWithBackground
import com.sirelon.sellsnap.designsystem.Input
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.performStepFeedback
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.media.PermissionDialogs
import com.sirelon.sellsnap.features.media.rememberPermissionController
import com.sirelon.sellsnap.features.media.rememberPhotoPickerController
import com.sirelon.sellsnap.features.media.ui.CameraGalleryPicker
import com.sirelon.sellsnap.features.media.ui.MAX_PHOTOS
import com.sirelon.sellsnap.features.media.ui.PhotosGrid
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.add_photos_to_continue
import com.sirelon.sellsnap.generated.resources.ai_hint_label
import com.sirelon.sellsnap.generated.resources.ai_hint_placeholder
import com.sirelon.sellsnap.generated.resources.generate_with_ai
import com.sirelon.sellsnap.generated.resources.ic_check
import com.sirelon.sellsnap.generated.resources.ic_snap_logo
import com.sirelon.sellsnap.generated.resources.ic_sparkles
import com.sirelon.sellsnap.generated.resources.new_listing
import com.sirelon.sellsnap.generated.resources.sellsnap_title
import com.sirelon.sellsnap.generated.resources.snap_photo_ad_desc
import com.sirelon.sellsnap.generated.resources.tip_angles
import com.sirelon.sellsnap.generated.resources.tip_defects
import com.sirelon.sellsnap.generated.resources.tip_lighting
import com.sirelon.sellsnap.generated.resources.tips_for_better_photos
import com.sirelon.sellsnap.generated.resources.turn_stuff_into_olx_listings
import com.sirelon.sellsnap.generated.resources.welcome_greeting
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val MAX_PROMPT_CHARS = 120

@Composable
fun GenerateAdScreen(
    openAdPreview: (AdvertisementWithAttributes) -> Unit,
    onWhisperClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GenerateAdViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionController = rememberPermissionController(permission = Permission.Camera)
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = state.isLoading,
        onBackCompleted = {},
    )

    val photoPicker = rememberPhotoPickerController(
        permissionController = permissionController,
        onResult = { selectionResult ->
            viewModel.onEvent(GenerateAdContract.GenerateAdEvent.UploadFilesResult(result = selectionResult))
        },
    )

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GenerateAdContract.GenerateAdEffect.ShowMessage -> {
                snackbarHostState.showSnackbar(effect.message)
            }

            is GenerateAdContract.GenerateAdEffect.OpenAdPreview -> openAdPreview(effect.ad)
        }
    }

    val hapticFeedback = LocalHapticFeedback.current

    AnimatedContent(state.isLoading) {
        if (it) {
            AiProcessingScreen(
                completedSteps = state.completedSteps,
                isGuestMode = state.isGuestMode,
                modifier = modifier,
            )
        } else {
            GenerateAdScreenContent(
                state = state,
                snackbarHostState = snackbarHostState,
                onPromptChanged = {
                    viewModel.onEvent(GenerateAdContract.GenerateAdEvent.PromptChanged(it))
                },
                onTakePhotoClick = { photoPicker.captureWithCamera() },
                onUploadClick = { photoPicker.pickFromGallery() },
                onRemovePhoto = { file ->
                    viewModel.onEvent(GenerateAdContract.GenerateAdEvent.RemovePhoto(file))
                },
                onSubmitClick = {
                    hapticFeedback.performStepFeedback()
                    viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
                },
                onWhisperClick = onWhisperClick,
                modifier = modifier,
            )
        }
    }

    PermissionDialogs(
        controller = permissionController,
    )
}

@Composable
private fun GenerateAdScreenContent(
    state: GenerateAdContract.GenerateAdState,
    snackbarHostState: SnackbarHostState,
    onPromptChanged: (String) -> Unit,
    onTakePhotoClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRemovePhoto: (KmpFile) -> Unit,
    onSubmitClick: () -> Unit,
    onWhisperClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SlimHeader(
                profileName = state.profileName,
                onWhisperClick = onWhisperClick,
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(AppDimens.Spacing.xl3),
            ) {
                MagicCtaBar(
                    hasPhotos = state.uploads.isNotEmpty(),
                    canSubmit = state.canSubmit,
                    onSubmitClick = onSubmitClick,
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .padding(horizontal = AppDimens.Spacing.xl3),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl6)
        ) {
            item {
                SellerHeader()
            }
            item {
                PageTitle()
            }

            item {
                PhotosGrid(
                    files = state.uploads,
                    onAddPhoto = onTakePhotoClick,
                    onRemovePhoto = onRemovePhoto,
                    interactionEnabled = !state.isLoading,
                )
            }
            if (state.uploads.size < MAX_PHOTOS) {
                item {
                    Box {
                        CameraGalleryPicker(
                            onCameraClick = onTakePhotoClick,
                            onGalleryClick = onUploadClick,
                            enabled = !state.isLoading,
                        )
                    }
                }
            }

            item {
                TipsSection()
            }
            item {
                PromptSection(
                    value = state.prompt,
                    enabled = !state.isLoading,
                    onValueChange = onPromptChanged,
                )
            }

            state.errorMessage?.let { errorMessage ->
                item {
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
}

@Composable
private fun MagicCtaBar(
    hasPhotos: Boolean,
    canSubmit: Boolean,
    onSubmitClick: () -> Unit,
) {
    AppButton(

        modifier = Modifier.fillMaxWidth(),
        style = AppButtonDefaults.magic(),
        text = if (hasPhotos)
            stringResource(Res.string.generate_with_ai)
        else
            stringResource(Res.string.add_photos_to_continue),
        onClick = {
            if (canSubmit) onSubmitClick()
        },
        leadingIcon = if (hasPhotos) painterResource(Res.drawable.ic_sparkles) else null,
    )
}

@Composable
private fun SlimHeader(
    profileName: String?,
    onWhisperClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryBrush = rememberPrimaryBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background)
            .statusBarsPadding()
            .padding(horizontal = AppDimens.Spacing.xl3)
            .padding(bottom = AppDimens.Spacing.xl3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.Size.xl10)
                    .clip(CircleShape)
                    .background(primaryBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_snap_logo),
                    contentDescription = null,
                    tint = AppTheme.colors.onPrimary,
                    modifier = Modifier.size(AppDimens.Size.xl6),
                )
            }
            Text(
                text = profileName ?: stringResource(Res.string.welcome_greeting),
                fontSize = AppDimens.TextSize.xl4,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
        ) {
            IconButton(onClick = onWhisperClick) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.Size.xl8)
                        .clip(CircleShape)
                        .background(AppTheme.colors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Whisper PoC",
                        tint = AppTheme.colors.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerHeader(
    modifier: Modifier = Modifier
) {
    val primaryBrush = rememberPrimaryBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl11))
            .background(primaryBrush)
            .padding(AppDimens.Spacing.xl6)
    ) {
        Box(
            modifier = Modifier
                .size(AppDimens.Size.xl21 + AppDimens.Size.xl)
                .align(Alignment.TopEnd)
                .offset(
                    x = AppDimens.Spacing.xl10,
                    y = -(AppDimens.Size.xl8 + AppDimens.Size.xl4),
                )
                .background(AppTheme.colors.onPrimary.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(AppDimens.Size.xl14 + AppDimens.Size.xl9)
                .align(Alignment.BottomStart)
                .offset(
                    x = -(AppDimens.Spacing.xl5 + AppDimens.Spacing.l),
                    y = AppDimens.Spacing.xl5 + AppDimens.Spacing.l,
                )
                .background(AppTheme.colors.onPrimary.copy(alpha = 0.1f), CircleShape)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl5)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl)
            ) {
                IconWithBackground(
                    modifier = Modifier.size(AppDimens.Size.xl11),
                    backgroundColor = AppTheme.colors.onPrimary,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_snap_logo),
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                    )
                }
                Text(
                    text = stringResource(Res.string.sellsnap_title),
                    color = AppTheme.colors.onPrimary,
                    fontSize = AppDimens.TextSize.xl6,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stringResource(Res.string.turn_stuff_into_olx_listings),
                color = AppTheme.colors.onPrimary,
                fontSize = AppDimens.TextSize.xl6,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = AppDimens.TextSize.xl8
            )

            Text(
                text = stringResource(Res.string.snap_photo_ad_desc),
                color = AppTheme.colors.onPrimary.copy(alpha = 0.9f),
                fontSize = AppDimens.TextSize.xl3,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PageTitle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
    ) {
        Text(
            text = stringResource(Res.string.new_listing),
            fontSize = AppDimens.TextSize.xl7,
            fontWeight = FontWeight.ExtraBold,
            color = AppTheme.colors.onSurface,
        )
        Text(
            text = "Add 1-$MAX_PHOTOS photos. AI will handle the rest.",
            fontSize = AppDimens.TextSize.xl3,
            fontWeight = FontWeight.Normal,
            color = AppTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun InfoSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl7),
        color = AppTheme.colors.surfaceHigh,
        shadowElevation = AppDimens.Size.xxs,
    ) {
        content()
    }
}

@Composable
private fun PromptSection(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoSection(modifier = modifier) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl6),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Text(
                text = stringResource(Res.string.ai_hint_label),
                fontSize = AppDimens.TextSize.xl5,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.onSurface,
            )
            Input(
                value = value,
                onValueChange = { onValueChange(it.take(MAX_PROMPT_CHARS)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(Res.string.ai_hint_placeholder),
                maxCharacters = MAX_PROMPT_CHARS,
            )
        }
    }
}

@Composable
private fun TipsSection(
    modifier: Modifier = Modifier
) {
    InfoSection(modifier = modifier) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)
            ) {
                IconWithBackground(
                    modifier = Modifier.size(AppDimens.Size.xl11),
                    backgroundColor = AppTheme.colors.surface,
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = AppTheme.colors.primary
                    )
                }

                Text(
                    text = stringResource(Res.string.tips_for_better_photos),
                    fontSize = AppDimens.TextSize.xl4,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.onSurface
                )
            }

            TipItem(text = stringResource(Res.string.tip_lighting))
            TipItem(text = stringResource(Res.string.tip_angles))
            TipItem(text = stringResource(Res.string.tip_defects))
        }
    }
}

@Composable
private fun TipItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(AppDimens.Size.xl4),
            tint = AppTheme.colors.success
        )
        Text(
            text = text,
            fontSize = AppDimens.TextSize.xl2,
            color = AppTheme.colors.onSurfaceMuted,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberPrimaryBrush(): Brush {
    val primaryBright = AppTheme.colors.primaryBright
    val primary = AppTheme.colors.primary
    return remember(primaryBright, primary) {
        Brush.linearGradient(listOf(primaryBright, primary))
    }
}

@PreviewLightDark
@Composable
private fun GenerateAdScreenEmptyPreview() {
    AppTheme {
        GenerateAdScreenContent(
            state = GenerateAdContract.GenerateAdState(),
            snackbarHostState = remember { SnackbarHostState() },
            onPromptChanged = {},
            onTakePhotoClick = {},
            onUploadClick = {},
            onRemovePhoto = {},
            onSubmitClick = {},
            onWhisperClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun GenerateAdScreenWithPromptPreview() {
    AppTheme {
        GenerateAdScreenContent(
            state = GenerateAdContract.GenerateAdState(
                prompt = "Nike Air Max 90, size 42, worn 2 months",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onPromptChanged = {},
            onTakePhotoClick = {},
            onUploadClick = {},
            onRemovePhoto = {},
            onSubmitClick = {},
            onWhisperClick = {},
        )
    }
}
