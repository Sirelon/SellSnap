package com.sirelon.sellsnap.features.seller.location.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.mohamedrejeb.calf.permissions.CoarseLocation
import com.mohamedrejeb.calf.permissions.Permission
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.designsystem.SearchInput
import com.sirelon.sellsnap.designsystem.dismissKeyboardOnTapOutside
import com.sirelon.sellsnap.designsystem.rememberKeyboardDismissAction
import com.sirelon.sellsnap.features.media.PermissionDialogContent
import com.sirelon.sellsnap.features.media.PermissionDialogs
import com.sirelon.sellsnap.features.media.rememberPermissionController
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEffect
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEvent
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerState
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.back
import com.sirelon.sellsnap.generated.resources.cancel
import com.sirelon.sellsnap.generated.resources.error_location_fetch_failed
import com.sirelon.sellsnap.generated.resources.got_it
import com.sirelon.sellsnap.generated.resources.location_detecting
import com.sirelon.sellsnap.generated.resources.location_picker_district_title
import com.sirelon.sellsnap.generated.resources.location_picker_no_results
import com.sirelon.sellsnap.generated.resources.location_picker_search_failed
import com.sirelon.sellsnap.generated.resources.location_picker_search_placeholder
import com.sirelon.sellsnap.generated.resources.location_picker_title
import com.sirelon.sellsnap.generated.resources.location_picker_use_current
import com.sirelon.sellsnap.generated.resources.location_rationale_message
import com.sirelon.sellsnap.generated.resources.location_rationale_title
import com.sirelon.sellsnap.generated.resources.location_services_off_message_android
import com.sirelon.sellsnap.generated.resources.location_services_off_message_ios
import com.sirelon.sellsnap.generated.resources.location_services_off_title
import com.sirelon.sellsnap.generated.resources.location_settings_message_android
import com.sirelon.sellsnap.generated.resources.location_settings_message_ios
import com.sirelon.sellsnap.generated.resources.location_settings_title
import com.sirelon.sellsnap.generated.resources.not_now
import com.sirelon.sellsnap.generated.resources.open_settings
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.platform.PlatformTargets
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Sets the selling city, by device location or by name. A city picked by name that has districts
 * asks for the district next. The chosen location becomes the saved default and is passed to
 * [onLocationSelected]. [districtsOf] opens the sheet on that city's district step.
 */
@Composable
fun LocationPickerSheet(
    onLocationSelected: (OlxLocation) -> Unit,
    districtsOf: OlxLocation? = null,
) {
    val viewModel: LocationPickerViewModel = koinViewModel { parametersOf(districtsOf) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionController = rememberPermissionController(permission = Permission.CoarseLocation)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LocationPickerEffect.RequestLocationPermission ->
                    permissionController.requestPermission {
                        viewModel.onEvent(LocationPickerEvent.DetectLocation)
                    }

                is LocationPickerEffect.LocationChosen -> onLocationSelected(effect.location)
            }
        }
    }

    PermissionDialogs(
        controller = permissionController,
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

    if (state.showServicesOffDialog) {
        LocationServicesOffDialog(onEvent = viewModel::onEvent)
    }

    val districtCity = state.districtCity
    val onDistrictStep = districtCity != null && state.districts.isNotEmpty()

    // Back on the district step goes to the city search, like the arrow, instead of closing the sheet.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = onDistrictStep,
        onBackCompleted = { viewModel.onEvent(LocationPickerEvent.BackToCitySearch) },
    )

    if (districtCity != null && onDistrictStep) {
        DistrictStepContent(city = districtCity, districts = state.districts, onEvent = viewModel::onEvent)
    } else {
        LocationPickerContent(state = state, onEvent = viewModel::onEvent)
    }
}

@Composable
private fun LocationPickerContent(
    state: LocationPickerState,
    onEvent: (LocationPickerEvent) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typography
    val dismissKeyboard = rememberKeyboardDismissAction()

    Column(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .dismissKeyboardOnTapOutside(dismissKeyboard),
    ) {
        Text(
            text = stringResource(Res.string.location_picker_title),
            style = t.title,
            color = c.onSurface,
            modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl5, vertical = AppDimens.Spacing.xl),
        )

        Cell(
            modifier = Modifier.testTag("location_picker_use_current"),
            headline = {
                Text(
                    text = stringResource(
                        if (state.isDetecting) Res.string.location_detecting else Res.string.location_picker_use_current,
                    ),
                    style = t.body.copy(fontWeight = FontWeight.Medium),
                    color = c.primary,
                )
            },
            supporting = if (state.detectFailed) {
                {
                    Text(
                        text = stringResource(Res.string.error_location_fetch_failed),
                        style = t.caption,
                        color = c.error,
                    )
                }
            } else null,
            leading = {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, tint = c.primary)
            },
            trailing = if (state.isDetecting) {
                { CircularProgressIndicator(modifier = Modifier.size(AppDimens.Size.xl3)) }
            } else null,
            onClick = if (state.isDetecting) null else {
                { onEvent(LocationPickerEvent.UseCurrentLocationClicked) }
            },
        )

        SearchInput(
            value = state.query,
            onValueChange = { onEvent(LocationPickerEvent.QueryChanged(it)) },
            placeholder = stringResource(Res.string.location_picker_search_placeholder),
            modifier = Modifier
                .padding(horizontal = AppDimens.Spacing.xl5, vertical = AppDimens.Spacing.xl)
                .testTag("location_picker_search"),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // A new search keeps the previous results on screen: swapping them for the spinner
                // mid-tap made the seller's tap land on nothing.
                state.isLoadingDistricts || (state.isSearching && state.results.isEmpty()) -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = AppDimens.Spacing.xl5),
                )

                state.searchFailed || state.districtsFailed -> Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(AppDimens.Spacing.xl5),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                ) {
                    Text(
                        text = stringResource(Res.string.location_picker_search_failed),
                        style = t.body,
                        color = c.onSurfaceMuted,
                    )
                    TextButton(
                        onClick = {
                            onEvent(
                                if (state.districtsFailed) {
                                    LocationPickerEvent.RetryDistricts
                                } else {
                                    LocationPickerEvent.RetrySearch
                                },
                            )
                        },
                    ) {
                        Text(stringResource(Res.string.retry))
                    }
                }

                state.searchedQuery != null && state.results.isEmpty() -> Text(
                    text = stringResource(Res.string.location_picker_no_results, state.searchedQuery),
                    style = t.body,
                    color = c.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(AppDimens.Spacing.xl5),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(state.results, key = { _, city -> city.location.cityId }) { index, city ->
                        Cell(
                            headline = {
                                Text(
                                    text = city.location.cityName,
                                    style = t.body.copy(fontWeight = FontWeight.Medium),
                                    color = c.onSurface,
                                )
                            },
                            supporting = city.region?.let { region ->
                                { Text(region, style = t.caption, color = c.onSurfaceMuted) }
                            },
                            onClick = { onEvent(LocationPickerEvent.CitySelected(city)) },
                        )
                        if (index < state.results.lastIndex) {
                            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.13f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistrictStepContent(
    city: OlxLocation,
    districts: List<OlxLocation>,
    onEvent: (LocationPickerEvent) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.typography

    Column(modifier = Modifier.fillMaxHeight(0.92f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = AppDimens.Spacing.xl,
                end = AppDimens.Spacing.xl5,
                top = AppDimens.Spacing.m,
                bottom = AppDimens.Spacing.m,
            ),
        ) {
            IconButton(onClick = { onEvent(LocationPickerEvent.BackToCitySearch) }) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    tint = c.onSurface,
                )
            }
            Text(
                text = stringResource(Res.string.location_picker_district_title, city.cityName),
                style = t.title,
                color = c.onSurface,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(districts, key = { _, district -> district.districtId ?: 0 }) { index, district ->
                Cell(
                    headline = {
                        Text(
                            text = district.districtName.orEmpty(),
                            style = t.body.copy(fontWeight = FontWeight.Medium),
                            color = c.onSurface,
                        )
                    },
                    onClick = { onEvent(LocationPickerEvent.DistrictSelected(district)) },
                )
                if (index < districts.lastIndex) {
                    HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.13f))
                }
            }
        }
    }
}

@Composable
private fun LocationServicesOffDialog(
    onEvent: (LocationPickerEvent) -> Unit,
    isIosDevice: Boolean = PlatformTargets.isIos(),
) {
    val dismiss = { onEvent(LocationPickerEvent.DismissServicesOffDialog) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(Res.string.location_services_off_title)) },
        text = {
            Text(
                stringResource(
                    if (isIosDevice) {
                        Res.string.location_services_off_message_ios
                    } else {
                        Res.string.location_services_off_message_android
                    },
                ),
            )
        },
        // iOS has no public link to the Location Services switch, so there is nothing to open.
        confirmButton = {
            if (isIosDevice) {
                TextButton(onClick = dismiss) { Text(stringResource(Res.string.got_it)) }
            } else {
                TextButton(onClick = { onEvent(LocationPickerEvent.OpenServicesSettings) }) {
                    Text(stringResource(Res.string.open_settings))
                }
            }
        },
        dismissButton = if (isIosDevice) null else {
            { TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) } }
        },
    )
}
