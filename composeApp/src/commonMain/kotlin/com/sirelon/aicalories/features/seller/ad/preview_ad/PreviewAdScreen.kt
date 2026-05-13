package com.sirelon.sellsnap.features.seller.ad.preview_ad

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.util.fastRoundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.mohamedrejeb.calf.permissions.CoarseLocation
import com.mohamedrejeb.calf.permissions.Permission
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.DigitOnlyInputTransformation
import com.sirelon.sellsnap.designsystem.ErrorPill
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.Pill
import com.sirelon.sellsnap.designsystem.ThousandSeparatorOutputTransformation
import com.sirelon.sellsnap.designsystem.TransparentInput
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.formatPrice
import com.sirelon.sellsnap.designsystem.pager.ImagesCarousel
import com.sirelon.sellsnap.features.media.PermissionController
import com.sirelon.sellsnap.features.media.PermissionDialogContent
import com.sirelon.sellsnap.features.media.PermissionDialogs
import com.sirelon.sellsnap.features.media.rememberPermissionController
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.ad.formatFriendlyElapsedTime
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent.CategorySelected
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PreviewBackInfoSheet
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishConfirmSheet
import com.sirelon.sellsnap.features.seller.ad.preview_ad.ui.PublishingScreen
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.domain.ValidationError
import com.sirelon.sellsnap.features.seller.categories.ui.AttributeItem
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ad_attributes_label
import com.sirelon.sellsnap.generated.resources.ad_category_label
import com.sirelon.sellsnap.generated.resources.ad_description_label
import com.sirelon.sellsnap.generated.resources.ad_location_label
import com.sirelon.sellsnap.generated.resources.ad_price_ai_estimated_range
import com.sirelon.sellsnap.generated.resources.ad_title_label
import com.sirelon.sellsnap.generated.resources.ad_your_price
import com.sirelon.sellsnap.generated.resources.banner_ready_in
import com.sirelon.sellsnap.generated.resources.cancel
import com.sirelon.sellsnap.generated.resources.copy_pill_copied
import com.sirelon.sellsnap.generated.resources.copy_pill_default
import com.sirelon.sellsnap.generated.resources.error_attr_above_maximum
import com.sirelon.sellsnap.generated.resources.error_attr_below_minimum
import com.sirelon.sellsnap.generated.resources.error_attr_invalid_selection
import com.sirelon.sellsnap.generated.resources.error_attr_multiple_values_not_allowed
import com.sirelon.sellsnap.generated.resources.error_attr_must_be_numeric
import com.sirelon.sellsnap.generated.resources.error_attr_required
import com.sirelon.sellsnap.generated.resources.guest_connect_olx_cta
import com.sirelon.sellsnap.generated.resources.guest_copy_hint
import com.sirelon.sellsnap.generated.resources.guest_mode_banner_message
import com.sirelon.sellsnap.generated.resources.guest_mode_banner_title
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_chevron_right
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.ic_circle_check_big
import com.sirelon.sellsnap.generated.resources.ic_copy
import com.sirelon.sellsnap.generated.resources.ic_layout_grid
import com.sirelon.sellsnap.generated.resources.ic_sparkles
import com.sirelon.sellsnap.generated.resources.location_detecting
import com.sirelon.sellsnap.generated.resources.location_not_available
import com.sirelon.sellsnap.generated.resources.location_rationale_message
import com.sirelon.sellsnap.generated.resources.location_rationale_title
import com.sirelon.sellsnap.generated.resources.location_settings_message_android
import com.sirelon.sellsnap.generated.resources.location_settings_message_ios
import com.sirelon.sellsnap.generated.resources.location_settings_title
import com.sirelon.sellsnap.generated.resources.not_now
import com.sirelon.sellsnap.generated.resources.open_settings
import com.sirelon.sellsnap.generated.resources.publish_errors
import com.sirelon.sellsnap.generated.resources.publish_on_olx
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.generated.resources.validation_all_valid
import com.sirelon.sellsnap.generated.resources.validation_error_desc_too_short
import com.sirelon.sellsnap.generated.resources.validation_error_no_category
import com.sirelon.sellsnap.generated.resources.validation_error_no_location
import com.sirelon.sellsnap.generated.resources.validation_error_title_too_short
import com.sirelon.sellsnap.generated.resources.validation_errors_more
import com.sirelon.sellsnap.generated.resources.validation_fields_remaining
import com.sirelon.sellsnap.navigation.BottomSheetSceneStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TitleMinLength = 10
private const val DescriptionMinLength = 30

@Composable
fun PreviewAdScreen(
    advertisement: AdvertisementWithAttributes,
    onBackToGenerate: () -> Unit,
    onChangeCategoryClick: () -> Unit,
    onPublishSuccess: (PublishSuccessData) -> Unit,
    pendingCategory: OlxCategory?,
    onCategoryConsumed: () -> Unit,
    onConnectOlxClick: () -> Unit,
    showImagesPreview: (List<String>, Int) -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
) {
    val viewModel: PreviewAdViewModel = koinViewModel { parametersOf(advertisement) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStack = remember {
        mutableStateListOf<PreviewAdDestination>(PreviewAdDestination.Content)
    }
    val sceneStrategies = remember {
        listOf(
            BottomSheetSceneStrategy<PreviewAdDestination>(),
            SinglePaneSceneStrategy<PreviewAdDestination>(),
        )
    }
    val dismissPublishConfirm: () -> Unit = {
        if (navBackStack.lastOrNull() is PreviewAdDestination.PublishConfirm) {
            navBackStack.removeAt(navBackStack.lastIndex)
        }
    }
    val dismissBackInfoSheet: () -> Unit = {
        if (navBackStack.lastOrNull() is PreviewAdDestination.BackInfo) {
            navBackStack.removeAt(navBackStack.lastIndex)
        }
    }
    val dismissPublishing: () -> Unit = {
        if (navBackStack.lastOrNull() is PreviewAdDestination.Publishing) {
            navBackStack.removeAt(navBackStack.lastIndex)
        }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is PreviewAdContract.PreviewAdEffect.ShowMessage ->
                snackbarHostState.showSnackbar(effect.message)

            PreviewAdContract.PreviewAdEffect.GoToGategoryPicker -> onChangeCategoryClick()

            PreviewAdContract.PreviewAdEffect.NavigateToPublishing -> {
                dismissPublishConfirm()
                if (navBackStack.lastOrNull() !is PreviewAdDestination.Publishing) {
                    navBackStack.add(PreviewAdDestination.Publishing)
                }
            }

            is PreviewAdContract.PreviewAdEffect.PublishSuccess -> {
                dismissPublishing()
                onPublishSuccess(effect.data)
            }

            is PreviewAdContract.PreviewAdEffect.PublishFailure -> {
                dismissPublishing()
                snackbarHostState.showSnackbar(effect.message)
            }

            is PreviewAdContract.PreviewAdEffect.NavigateToProfile -> {
                dismissPublishing()
                onNavigateToProfile(effect.reason)
            }
        }
    }

    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = navBackStack,
        onBack = {
            when (navBackStack.lastOrNull()) {
                is PreviewAdDestination.Content -> navBackStack.add(PreviewAdDestination.BackInfo)
                is PreviewAdDestination.Publishing -> Unit
                else -> {
                    if (navBackStack.size > 1) {
                        navBackStack.removeAt(navBackStack.lastIndex)
                    }
                }
            }
        },
        sceneStrategies = sceneStrategies,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<PreviewAdDestination>()),
        entryProvider = entryProvider {
            entry<PreviewAdDestination.Content> {
                PreviewAdContentRoute(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onChangeCategoryClick = onChangeCategoryClick,
                    pendingCategory = pendingCategory,
                    onCategoryConsumed = onCategoryConsumed,
                    onConnectOlxClick = onConnectOlxClick,
                    onPublishConfirmationRequested = {
                        if (navBackStack.lastOrNull() !is PreviewAdDestination.PublishConfirm) {
                            navBackStack.add(PreviewAdDestination.PublishConfirm)
                        }
                    },
                    showImagesPreview = showImagesPreview,
                )
            }

            entry<PreviewAdDestination.PublishConfirm>(
                metadata = BottomSheetSceneStrategy.bottomSheet(),
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()

                PublishConfirmSheet(
                    imageUrl = state.images.firstOrNull(),
                    title = viewModel.titleState.text.toString(),
                    categoryLabel = state.categoryLabel,
                    priceFormatted = "₴ ${formatPrice(state.price)}",
                    onConfirm = { viewModel.onEvent(PreviewAdEvent.Publish) },
                    onDismiss = dismissPublishConfirm,
                )
            }

            entry<PreviewAdDestination.BackInfo>(
                metadata = BottomSheetSceneStrategy.bottomSheet(),
            ) {
                PreviewBackInfoSheet(
                    onStay = dismissBackInfoSheet,
                    onLeave = {
                        dismissBackInfoSheet()
                        onBackToGenerate()
                    },
                )
            }

            entry<PreviewAdDestination.Publishing> {
                PublishingScreen()
            }
        },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PreviewAdContentRoute(
    viewModel: PreviewAdViewModel,
    snackbarHostState: SnackbarHostState,
    onChangeCategoryClick: () -> Unit,
    pendingCategory: OlxCategory?,
    onCategoryConsumed: () -> Unit,
    onConnectOlxClick: () -> Unit,
    onPublishConfirmationRequested: () -> Unit,
    showImagesPreview: (List<String>, Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locationPermissionController =
        rememberPermissionController(permission = Permission.CoarseLocation)

    LaunchedEffect(pendingCategory) {
        if (pendingCategory != null) {
            viewModel.onEvent(CategorySelected(pendingCategory))
            onCategoryConsumed()
        }
    }

    if (state.isSessionResolved && !state.isGuest) {
        LocationPermissionsBlock(viewModel::onEvent, locationPermissionController)
    }

    val titleTooShortLabel = stringResource(Res.string.validation_error_title_too_short)
    val descTooShortLabel = stringResource(Res.string.validation_error_desc_too_short)
    val noCategoryLabel = stringResource(Res.string.validation_error_no_category)
    val noLocationLabel = stringResource(Res.string.validation_error_no_location)

    // @Composable reads of TextFieldState.text trigger recomposition on change.
    val titleText = viewModel.titleState.text
    val descText = viewModel.descriptionState.text
    val validationErrors = buildList {
        if (titleText.length < 10) add(titleTooShortLabel)
        if (descText.length < 30) add(descTooShortLabel)
        if (state.isSessionResolved && !state.isGuest) {
            if (state.selectedCategory == null) add(noCategoryLabel)
            if (state.location == null) add(noLocationLabel)
            for (item in state.attributeItems) {
                when {
                    item.error != null ->
                        add("${item.attribute.label}: ${item.error.toDisplayString()}")

                    item.attribute.validationRules.required && item.selectedValues.isEmpty() ->
                        add(item.attribute.label)
                }
            }
        }
    }
    val isValid = validationErrors.isEmpty()
    val firstInvalidAttributeCode = state.attributeItems
        .firstOrNull { item ->
            item.error != null || (item.attribute.validationRules.required && item.selectedValues.isEmpty())
        }
        ?.attribute
        ?.code

    var showErrors by remember { mutableStateOf(false) }
    var autoOpenAttributeCode by remember { mutableStateOf<String?>(null) }
    var autoOpenAttributeRequest by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val invalidAttributeBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(autoOpenAttributeRequest) {
        if (autoOpenAttributeRequest > 0) {
            invalidAttributeBringIntoViewRequester.bringIntoView()
        }
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.Spacing.xl3)
                    .padding(bottom = AppDimens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            ) {
                if (state.isSessionResolved && state.isGuest) {
                    GuestModeBanner()
                    AppButton(
                        modifier = Modifier.fillMaxWidth(),
                        style = AppButtonDefaults.primary(),
                        text = stringResource(Res.string.guest_connect_olx_cta),
                        onClick = onConnectOlxClick,
                    )
                } else if (state.isSessionResolved) {
                    AppButton(
                        modifier = Modifier.fillMaxWidth(),
                        style = if (isValid) AppButtonDefaults.success() else AppButtonDefaults.primary(),
                        text = if (isValid) {
                            stringResource(Res.string.publish_on_olx)
                        } else {
                            stringResource(Res.string.publish_errors, validationErrors.size)
                        },
                        trailingIcon = painterResource(Res.drawable.ic_arrow_right),
                        onClick = {
                            if (!isValid) {
                                showErrors = true
                                val attributeCode = firstInvalidAttributeCode
                                if (attributeCode != null) {
                                    autoOpenAttributeCode = attributeCode
                                    autoOpenAttributeRequest += 1
                                } else {
                                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                                }
                            } else {
                                onPublishConfirmationRequested()
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .verticalScroll(scrollState)
                .padding(bottom = AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3)
        ) {
            ImagesCarousel(
                images = state.images,
                onImageClick = {
                    showImagesPreview(state.images, it)
                },
            )

            ReadyBanner(
                elapsedMs = state.generationElapsedMs,
                modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
            )

            AnimatedVisibility(
                visible = showErrors && !isValid,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ValidationBanner(
                    errors = validationErrors,
                    modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                )
            }

            PreviewAdContent(
                onEvent = viewModel::onEvent,
                state = state,
                titleState = viewModel.titleState,
                descriptionState = viewModel.descriptionState,
                locationPermissionController = if (state.isSessionResolved && !state.isGuest) locationPermissionController else null,
                autoOpenAttributeCode = autoOpenAttributeCode,
                autoOpenAttributeRequest = autoOpenAttributeRequest,
                autoOpenAttributeModifier = Modifier.bringIntoViewRequester(invalidAttributeBringIntoViewRequester),
            )
            if (state.isSessionResolved && !state.isGuest) {
                ValidationStatusCard(
                    modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                    isValid = isValid,
                    errorCount = validationErrors.size,
                )
            }
        }
    }
}

@Composable
private fun ReadyBanner(
    elapsedMs: Long,
    modifier: Modifier = Modifier,
) {
    val successColor = AppTheme.colors.success

    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = successColor.copy(alpha = 0.12f),
        contentColor = successColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Spacing.xl3)
                .padding(vertical = AppDimens.Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_circle_check_big),
                contentDescription = null,
                tint = successColor,
                modifier = Modifier.size(AppDimens.Size.xl5),
            )
            Text(
                text = stringResource(
                    Res.string.banner_ready_in,
                    formatFriendlyElapsedTime(elapsedMs),
                ),
                style = AppTheme.typography.body,
                color = successColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ValidationBanner(
    errors: List<String>,
    modifier: Modifier = Modifier,
) {
    val errorColor = AppTheme.colors.error
    val displayErrors = errors.take(3)
    val remaining = errors.size - displayErrors.size

    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = errorColor.copy(alpha = 0.12f),
        contentColor = errorColor,
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.Spacing.xl3),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_circle_alert),
                contentDescription = null,
                tint = errorColor,
                modifier = Modifier
                    .size(AppDimens.Size.xl5)
                    .padding(top = AppDimens.Spacing.xs2),
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs)) {
                displayErrors.forEach { error ->
                    Text(
                        text = "• $error",
                        style = AppTheme.typography.body,
                        color = errorColor,
                    )
                }
                if (remaining > 0) {
                    Text(
                        text = stringResource(Res.string.validation_errors_more, remaining),
                        style = AppTheme.typography.caption,
                        color = errorColor.copy(alpha = 0.70f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationStatusCard(
    isValid: Boolean,
    errorCount: Int,
    modifier: Modifier = Modifier,
) {
    val bgColor: Color
    val contentColor: Color
    val icon: Painter
    val text: String

    if (isValid) {
        bgColor = AppTheme.colors.success.copy(alpha = 0.12f)
        contentColor = AppTheme.colors.success
        icon = painterResource(Res.drawable.ic_circle_check_big)
        text = stringResource(Res.string.validation_all_valid)
    } else {
        bgColor = AppTheme.colors.warning.copy(alpha = 0.12f)
        contentColor = AppTheme.colors.warning
        icon = painterResource(Res.drawable.ic_circle_alert)
        text = stringResource(Res.string.validation_fields_remaining, errorCount)
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = bgColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = AppDimens.Spacing.xl3)
                .padding(vertical = AppDimens.Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(AppDimens.Size.xl5),
            )
            Text(
                text = text,
                style = AppTheme.typography.body,
                color = contentColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun GuestModeBanner(modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = AppTheme.colors.warning.copy(alpha = 0.12f),
        contentColor = AppTheme.colors.warning,
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.guest_mode_banner_title),
                style = AppTheme.typography.subTitle,
                color = AppTheme.colors.warning,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.guest_mode_banner_message),
                style = AppTheme.typography.body,
                color = AppTheme.colors.warning,
            )
        }
    }
}

@Composable
private fun GuestCopyHint() {
    Text(
        text = stringResource(Res.string.guest_copy_hint),
        style = AppTheme.typography.caption,
        color = AppTheme.colors.onSurfaceMuted,
    )
}

@Composable
private fun LocationPermissionsBlock(
    onEvent: (PreviewAdEvent) -> Unit,
    locationPermissionController: PermissionController,
) {
    PermissionDialogs(
        controller = locationPermissionController,
        rationaleContent = PermissionDialogContent(
            title = Res.string.location_rationale_title,
            message = Res.string.location_rationale_message,
            confirmText = Res.string.retry,
            dismissText = Res.string.not_now,
        ),
        settingsContentProvider = { isIos ->
            PermissionDialogContent(
                title = Res.string.location_settings_title,
                message = if (isIos) {
                    Res.string.location_settings_message_ios
                } else {
                    Res.string.location_settings_message_android
                },
                confirmText = Res.string.open_settings,
                dismissText = Res.string.cancel,
            )
        },
    )

    LaunchedEffect(Unit) {
        locationPermissionController.requestPermission {
            onEvent(PreviewAdEvent.FetchLocation)
        }
    }
}

@Composable
private fun PreviewAdContent(
    titleState: TextFieldState,
    descriptionState: TextFieldState,
    state: PreviewAdContract.PreviewAdState,
    onEvent: (PreviewAdEvent) -> Unit,
    autoOpenAttributeCode: String?,
    autoOpenAttributeRequest: Int,
    autoOpenAttributeModifier: Modifier,
    locationPermissionController: PermissionController? = null,
) {
    val titleText = titleState.text.toString()
    val descriptionText = descriptionState.text.toString()
    val isTitleInvalid = remember(titleText) { titleText.trim().length < TitleMinLength }
    val isDescriptionInvalid =
        remember(descriptionText) { descriptionText.trim().length < DescriptionMinLength }

    Column(
        modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl)
    ) {
        AdTitleCard(
            titleState = titleState,
            isInvalid = isTitleInvalid,
        )

        AdDescriptionCard(
            descriptionState = descriptionState,
            isInvalid = isDescriptionInvalid,
        )

        if (state.isSessionResolved && state.isGuest) {
            GuestCopyHint()
        }

        val priceTextFieldState = rememberTextFieldState(state.price.roundToInt().toString())

        LaunchedEffect(null) {
            snapshotFlow {
                priceTextFieldState.text
            }
                .distinctUntilChanged()
                .map { it.toString().toFloatOrNull() }
                .filterNotNull()
                .collect {
                    onEvent(PreviewAdEvent.OnPriceChanged(it))
                }
        }

        AdPriceCard(
            priceTextFieldState = priceTextFieldState,
            minPrice = state.minPrice,
            maxPrice = state.maxPrice,
        )

        if (state.isSessionResolved && !state.isGuest) {
            AdCategoryCard(
                categoryLabel = state.categoryLabel,
                onChangeClick = { onEvent(PreviewAdEvent.OnChangeCategoryClick) },
            )

            if (state.attributeItems.isNotEmpty()) {
                AdAttributesCard(
                    items = state.attributeItems,
                    onEvent = onEvent,
                    autoOpenAttributeCode = autoOpenAttributeCode,
                    autoOpenAttributeRequest = autoOpenAttributeRequest,
                    autoOpenAttributeModifier = autoOpenAttributeModifier,
                )
            }

            AdLocationCard(
                location = state.location,
                isLoading = state.locationLoading,
                onRefreshClick = {
                    locationPermissionController?.requestPermission {
                        onEvent(PreviewAdEvent.RefreshLocationClicked)
                    }
                },
            )
        }
    }
}

@Composable
private fun PreviewSectionCard(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = {
            onClick?.invoke()
        },
    ) {
        Column(modifier = Modifier.padding(vertical = AppDimens.Spacing.xl3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Spacing.xl3),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = label,
                    style = AppTheme.typography.subTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                if (headerTrailing != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        headerTrailing()
                    }
                }
            }

            content()
        }
    }
}

@Composable
private fun PreviewSectionInputCard(
    label: String,
    textFieldState: TextFieldState,
    maxCharacters: Int,
    isInvalid: Boolean = false,
    headerTrailing: (@Composable () -> Unit)? = null,
) {
    PreviewSectionCard(
        label = label,
        headerTrailing = headerTrailing,
        content = {
            TransparentInput(
                state = textFieldState,
                maxCharacters = maxCharacters,
                isError = isInvalid,

                )
        }
    )
}

@Composable
private fun AdTitleCard(
    titleState: TextFieldState,
    isInvalid: Boolean,
) {
    PreviewSectionInputCard(
        label = stringResource(Res.string.ad_title_label),
        textFieldState = titleState,
        maxCharacters = 140,
        isInvalid = isInvalid,
        headerTrailing = {
            if (isInvalid) {
                ErrorPill()
            }
            CopyPill(value = titleState.text.toString())
            AiGeneratedBadge()
        },
    )
}

@Composable
private fun AdDescriptionCard(
    descriptionState: TextFieldState,
    isInvalid: Boolean,
) {
    PreviewSectionInputCard(
        label = stringResource(Res.string.ad_description_label),
        textFieldState = descriptionState,
        maxCharacters = 9000,
        isInvalid = isInvalid,
        headerTrailing = {
            if (isInvalid) {
                ErrorPill()
            }
            CopyPill(value = descriptionState.text.toString())
            AiGeneratedBadge()
        },
    )
}

@Composable
private fun AdPriceCard(
    priceTextFieldState: TextFieldState,
    minPrice: Float,
    maxPrice: Float,
) {
    val textStyle = AppTheme.typography.headline
    PreviewSectionCard(label = stringResource(Res.string.ad_your_price)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m)) {
            val price = remember(priceTextFieldState.text) {
                (priceTextFieldState.text.toString().toFloatOrNull()
                    ?: ((maxPrice + minPrice) / 2f))
                    .coerceIn(minPrice, maxPrice)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ProvideTextStyle(textStyle) {
                    TransparentInput(
                        state = priceTextFieldState,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = DigitOnlyInputTransformation,
                        outputTransformation = ThousandSeparatorOutputTransformation,
                        prefix = {
                            // TODO: change currency (SIR-15)
                            Text(text = "₴", style = textStyle)
                        },
                    )
                }

                CopyPill(
                    modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                    value = price.toString(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Spacing.xl3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPrice(minPrice),
                    style = AppTheme.typography.label,
                    color = AppTheme.colors.onSurface,
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    value = price,
                    onValueChange = {
                        priceTextFieldState.setTextAndPlaceCursorAtEnd(
                            it.fastRoundToInt().toString()
                        )
                    },
                    valueRange = minPrice..maxPrice,
                )
                Text(
                    text = formatPrice(maxPrice),
                    style = AppTheme.typography.label,
                )
            }

            Text(
                modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                text = stringResource(
                    Res.string.ad_price_ai_estimated_range,
                    formatPrice(minPrice),
                    formatPrice(maxPrice),
                ),
                style = AppTheme.typography.caption,
                color = AppTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun PreviewSectionClickableCard(
    label: String,
    icon: Painter,
    content: @Composable () -> Unit,
    onClick: () -> Unit
) {
    PreviewSectionCard(label = label, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = AppDimens.Spacing.xl3)
                .padding(top = AppDimens.Spacing.xl3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.Size.xl6),
                    tint = AppTheme.colors.onSurfaceMuted,
                )
                content()
            }
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier,
                tint = AppTheme.colors.onSurfaceMuted,
            )
        }
    }
}


@Composable
private fun AdCategoryCard(categoryLabel: String, onChangeClick: () -> Unit) {
    PreviewSectionClickableCard(
        label = stringResource(Res.string.ad_category_label),
        onClick = onChangeClick,
        icon = painterResource(Res.drawable.ic_layout_grid),
        content = {
            Text(
                text = categoryLabel,
                style = AppTheme.typography.body,
                fontWeight = FontWeight.Bold,
            )
        }
    )
}

@Composable
private fun AdAttributesCard(
    items: List<OlxAttributeState>,
    onEvent: (PreviewAdEvent) -> Unit,
    autoOpenAttributeCode: String?,
    autoOpenAttributeRequest: Int,
    autoOpenAttributeModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    PreviewSectionCard(
        label = stringResource(Res.string.ad_attributes_label),
        modifier = modifier,
    ) {
        Column {
            items.forEach { item ->
                key(item.attribute.code) {
                    val shouldAutoOpen = item.attribute.code == autoOpenAttributeCode
                    AttributeItem(
                        attribute = item.attribute,
                        selectedValues = item.selectedValues,
                        onSelectionChange = { values ->
                            onEvent(
                                PreviewAdEvent.AttributeValueChanged(
                                    attributeCode = item.attribute.code,
                                    values = values
                                )
                            )
                        },
                        validationError = item.error,
                        autoOpenRequest = if (shouldAutoOpen) {
                            autoOpenAttributeRequest
                        } else {
                            0
                        },
                        modifier = if (shouldAutoOpen) autoOpenAttributeModifier else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdLocationCard(
    location: OlxLocation?,
    isLoading: Boolean,
    onRefreshClick: () -> Unit = {},
) {
    PreviewSectionClickableCard(
        label = stringResource(Res.string.ad_location_label),
        onClick = onRefreshClick,
        icon = rememberVectorPainter(Icons.Default.LocationOn),
        content = {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(AppDimens.Size.xl4))
                    Text(
                        text = stringResource(Res.string.location_detecting),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.outline,
                    )
                }

                location != null -> {
                    Text(
                        text = location.displayName,
                        style = AppTheme.typography.body,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(Res.string.location_not_available),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.outline,
                    )
                }
            }
        },
    )
}

@Composable
private fun AiGeneratedBadge(
    label: String = "AI",
    modifier: Modifier = Modifier,
) {
    Pill(
        color = AppTheme.colors.primary,
        text = label,
        iconResource = Res.drawable.ic_sparkles,
        modifier = modifier
    )
}

@Composable
fun CopyPill(
    value: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val containerColor = if (copied) {
        AppTheme.colors.success.copy(alpha = 0.18f)
    } else {
        AppTheme.colors.surfaceLow
    }
    val contentColor = if (copied) AppTheme.colors.success else AppTheme.colors.primary

    Pill(
        bgColor = containerColor,
        color = contentColor,
        onClick = {
            scope.launch {
                clipboard.setText(AnnotatedString(value))
                copied = true
            }
        },
        text = stringResource(if (copied) Res.string.copy_pill_copied else Res.string.copy_pill_default),
        iconResource = if (copied) Res.drawable.ic_circle_check_big else Res.drawable.ic_copy,
        modifier = modifier
    )

    if (copied) {
        LaunchedEffect(Unit) {
            delay(1400L.milliseconds)
            copied = false
        }
    }
}


@Composable
private fun ValidationError.toDisplayString(): String = when (this) {
    ValidationError.Required -> stringResource(Res.string.error_attr_required)
    ValidationError.MustBeNumeric -> stringResource(Res.string.error_attr_must_be_numeric)
    is ValidationError.BelowMinimum -> stringResource(Res.string.error_attr_below_minimum, min)
    is ValidationError.AboveMaximum -> stringResource(Res.string.error_attr_above_maximum, max)
    is ValidationError.InvalidSelection -> stringResource(Res.string.error_attr_invalid_selection)
    ValidationError.MultipleValuesNotAllowed -> stringResource(Res.string.error_attr_multiple_values_not_allowed)
}

// region Previews

@PreviewLightDark
@Composable
private fun ValidationBannerPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            ValidationBanner(
                errors = listOf(
                    "Title: at least 10 characters",
                    "Description: at least 30 characters",
                    "Select a category",
                    "Add your location",
                ),
                modifier = Modifier.padding(AppDimens.Spacing.xl3),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ValidationStatusCardValidPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            ValidationStatusCard(
                isValid = true,
                errorCount = 0,
                modifier = Modifier.padding(AppDimens.Spacing.xl3),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ValidationStatusCardInvalidPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            ValidationStatusCard(
                isValid = false,
                errorCount = 3,
                modifier = Modifier.padding(AppDimens.Spacing.xl3),
            )
        }
    }
}


@PreviewLightDark
@Composable
private fun AdPriceCardMinPreview() {
    AppTheme {
        Surface {
            AdPriceCard(
                priceTextFieldState = rememberTextFieldState("1000"),
                minPrice = 1000f,
                maxPrice = 50000f,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewAdEditableSectionsValidPreview() {
    PreviewAdEditableSectionsPreview(
        title = "Nike Air Max 90 Size 42",
        description = "Well-kept sneakers with minor wear on the outsole and clean upper panels.",
    )
}

@PreviewLightDark
@Composable
private fun AdPriceCardMidPreview() {
    AppTheme {
        Surface {
            AdPriceCard(
                priceTextFieldState = rememberTextFieldState("25500"),
                minPrice = 1000f,
                maxPrice = 50000f,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewAdEditableSectionsInvalidPreview() {
    PreviewAdEditableSectionsPreview(
        title = "Too short",
        description = "Needs more detail",
    )
}

@Composable
private fun PreviewAdEditableSectionsPreview(
    title: String,
    description: String,
) {
    AppTheme {
        Surface(
            color = AppTheme.colors.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.Spacing.xl3),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl),
            ) {
                AdTitleCard(
                    titleState = rememberTextFieldState(title),
                    isInvalid = title.trim().length < TitleMinLength,
                )
                AdDescriptionCard(
                    descriptionState = rememberTextFieldState(description),
                    isInvalid = description.trim().length < DescriptionMinLength,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AdPriceCardMaxPreview() {
    AppTheme {
        Surface {
            AdPriceCard(
                priceTextFieldState = rememberTextFieldState("50000"),
                minPrice = 1000f,
                maxPrice = 50000f,
            )
        }
    }
}

// endregion
