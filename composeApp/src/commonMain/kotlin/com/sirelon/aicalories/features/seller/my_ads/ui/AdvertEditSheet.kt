package com.sirelon.sellsnap.features.seller.my_ads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.DigitOnlyInputTransformation
import com.sirelon.sellsnap.designsystem.formatPrice
import com.sirelon.sellsnap.designsystem.ThousandSeparatorOutputTransformation
import com.sirelon.sellsnap.designsystem.TransparentInput
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AdvertEdit
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_in_progress
import com.sirelon.sellsnap.generated.resources.advert_confirm_cancel
import com.sirelon.sellsnap.generated.resources.advert_edit_description_label
import com.sirelon.sellsnap.generated.resources.advert_edit_load_failed
import com.sirelon.sellsnap.generated.resources.advert_edit_photos_note
import com.sirelon.sellsnap.generated.resources.advert_edit_price_label
import com.sirelon.sellsnap.generated.resources.advert_edit_save
import com.sirelon.sellsnap.generated.resources.advert_edit_subtitle
import com.sirelon.sellsnap.generated.resources.advert_edit_title
import com.sirelon.sellsnap.generated.resources.advert_edit_title_label
import com.sirelon.sellsnap.generated.resources.advert_edit_change_price
import com.sirelon.sellsnap.generated.resources.advert_edit_will_change
import org.jetbrains.compose.resources.stringResource

/**
 * Edits a live listing's text and price and pushes it back with `PUT adverts/{id}` (SIR-104).
 *
 * Dropping the price on something that is not selling is most of the demand, so the price field
 * sits with the text in one short sheet two taps from My Ads rather than behind a re-run of the
 * whole publish flow.
 *
 * Photos and attributes are deliberately not editable here. `PUT` takes the full create payload
 * and resets whatever is omitted, so the edit re-sends the exact JSON OLX returned with only
 * these three fields replaced - nothing the seller did not touch can be lost. Whether OLX accepts
 * its own image URLs back, and whether single-select attributes come back as a scalar or an array,
 * are unverified against a real advert; until they are, offering photo and attribute editing
 * risks a silently lossy save. See `SPIKE-SIR-99-advert-edit-round-trip.md`.
 */
@Composable
fun AdvertEditSheet(
    edit: AdvertEdit,
    onSubmit: (title: String, description: String, price: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advert_edit_sheet")
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        Text(
            text = stringResource(Res.string.advert_edit_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )

        when {
            edit.isLoading -> Text(
                text = stringResource(Res.string.advert_action_in_progress),
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
            )

            edit.loadFailed -> {
                Text(
                    text = stringResource(Res.string.advert_edit_load_failed),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.error,
                )
                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.advert_confirm_cancel),
                    onClick = onDismiss,
                    style = AppButtonDefaults.secondary(),
                )
            }

            // Keyed on the advert so the text fields below are re-seeded rather than carrying
            // the previously edited listing's text across.
            else -> key(edit.advert.id) {
                EditForm(edit = edit, onSubmit = onSubmit, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun EditForm(
    edit: AdvertEdit,
    onSubmit: (String, String, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val titleState = rememberTextFieldState(edit.title)
    val descriptionState = rememberTextFieldState(edit.description)
    val priceState = rememberTextFieldState(edit.priceValue?.toString().orEmpty())

    Text(
        text = stringResource(Res.string.advert_edit_subtitle),
        style = AppTheme.typography.body,
        color = AppTheme.colors.onSurfaceMuted,
    )

    FieldLabel(stringResource(Res.string.advert_edit_title_label))
    TransparentInput(
        state = titleState,
        modifier = Modifier.testTag("advert_edit_title_input"),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
    )

    FieldLabel(stringResource(Res.string.advert_edit_price_label))
    TransparentInput(
        state = priceState,
        modifier = Modifier.testTag("advert_edit_price_input"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        inputTransformation = DigitOnlyInputTransformation,
        outputTransformation = ThousandSeparatorOutputTransformation,
    )

    FieldLabel(stringResource(Res.string.advert_edit_description_label))
    TransparentInput(
        state = descriptionState,
        modifier = Modifier.testTag("advert_edit_description_input"),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
    )

    Text(
        text = stringResource(Res.string.advert_edit_photos_note),
        style = AppTheme.typography.caption,
        color = AppTheme.colors.onSurfaceMuted,
    )

    val editedTitle = titleState.text.toString().trim()
    val editedDescription = descriptionState.text.toString().trim()
    // A cleared field means "leave the price alone", matching what the ViewModel sends - so it
    // must not be listed here as a change either.
    val editedPrice = priceState.text.toString().toLongOrNull()?.takeIf { it != edit.priceValue }
    ChangeSummary(
        titleChanged = editedTitle != edit.title,
        descriptionChanged = editedDescription != edit.description,
        currentPrice = edit.priceValue,
        newPrice = editedPrice,
    )

    AppButton(
        modifier = Modifier.fillMaxWidth().testTag("advert_edit_save"),
        text = if (edit.isSaving) {
            stringResource(Res.string.advert_action_in_progress)
        } else {
            stringResource(Res.string.advert_edit_save)
        },
        enabled = !edit.isSaving,
        onClick = {
            onSubmit(
                titleState.text.toString().trim(),
                descriptionState.text.toString().trim(),
                priceState.text.toString().toLongOrNull(),
            )
        },
        style = AppButtonDefaults.primary(),
    )
    AppButton(
        modifier = Modifier.fillMaxWidth().testTag("advert_edit_cancel"),
        text = stringResource(Res.string.advert_confirm_cancel),
        enabled = !edit.isSaving,
        onClick = onDismiss,
        style = AppButtonDefaults.secondary(),
    )
}

/**
 * What this save will actually send, in the same spirit as the publish confirmation: the seller
 * sees the change before it goes, without a second sheet in the way of the two-tap price drop
 * this feature exists for. Renders nothing until something differs from what OLX returned, so an
 * accidental Edit tap is a no-op the seller can see is a no-op.
 */
@Composable
private fun ChangeSummary(
    titleChanged: Boolean,
    descriptionChanged: Boolean,
    currentPrice: Long?,
    newPrice: Long?,
) {
    if (!titleChanged && !descriptionChanged && newPrice == null) return

    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs)) {
        Text(
            text = stringResource(Res.string.advert_edit_will_change),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold,
        )
        if (newPrice != null) {
            ChangeLine(
                stringResource(
                    Res.string.advert_edit_change_price,
                    currentPrice?.let { formatPrice(it.toFloat()) }.orEmpty(),
                    formatPrice(newPrice.toFloat()),
                ),
            )
        }
        if (titleChanged) ChangeLine(stringResource(Res.string.advert_edit_title_label))
        if (descriptionChanged) ChangeLine(stringResource(Res.string.advert_edit_description_label))
    }
}

@Composable
private fun ChangeLine(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.caption,
        color = AppTheme.colors.onSurface,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.caption,
        color = AppTheme.colors.onSurfaceMuted,
    )
}

@PreviewLightDark
@Composable
private fun AdvertEditSheetPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            AdvertEditSheet(
                edit = AdvertEdit(
                    localIndex = 1,
                    advert = MyAdvertItem(
                        id = 1,
                        title = "Nike Air Max 90",
                        status = AdvertStatus.Active,
                        url = "",
                        primaryImageUrl = null,
                        priceFormatted = "₴ 1 800",
                        priceValue = 1800,
                        currencyCode = "UAH",
                        createdAt = "",
                        validTo = "",
                    ),
                    isLoading = false,
                    title = "Nike Air Max 90, size 42, worn 2 months",
                    description = "Bought last spring, worn a handful of times.",
                    priceValue = 1800,
                ),
                onSubmit = { _, _, _ -> },
                onDismiss = {},
            )
        }
    }
}
