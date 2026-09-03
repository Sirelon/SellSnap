package com.sirelon.sellsnap.features.seller.my_ads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.DigitOnlyInputTransformation
import com.sirelon.sellsnap.designsystem.dismissKeyboardOnTapOutside
import com.sirelon.sellsnap.designsystem.rememberKeyboardDismissAction
import com.sirelon.sellsnap.designsystem.ThousandSeparatorOutputTransformation
import com.sirelon.sellsnap.designsystem.TransparentInput
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.SoldPrompt
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_in_progress
import com.sirelon.sellsnap.generated.resources.advert_confirm_deactivate_message
import com.sirelon.sellsnap.generated.resources.advert_sold_price_confirm
import com.sirelon.sellsnap.generated.resources.advert_sold_price_label
import com.sirelon.sellsnap.generated.resources.advert_sold_price_message
import com.sirelon.sellsnap.generated.resources.advert_sold_price_skip
import com.sirelon.sellsnap.generated.resources.advert_sold_price_title
import com.sirelon.sellsnap.generated.resources.advert_sold_prompt_message
import com.sirelon.sellsnap.generated.resources.advert_sold_prompt_no
import com.sirelon.sellsnap.generated.resources.advert_sold_prompt_title
import com.sirelon.sellsnap.generated.resources.advert_sold_prompt_yes
import org.jetbrains.compose.resources.stringResource

/**
 * OLX's "did it sell?" (SIR-102), which `deactivate` will not be accepted without.
 *
 * Deliberately one sheet with at most two steps. It fires on an action the seller wants to
 * finish quickly, and a prompt that feels like an interrogation trains them to deactivate on OLX
 * instead - which loses both the outcome data and the reason to open this app.
 */
@Composable
fun MarkAsSoldSheet(
    prompt: SoldPrompt,
    onAnswer: (isSold: Boolean) -> Unit,
    onPriceSubmitted: (price: Long?) -> Unit,
) {
    val dismissKeyboard = rememberKeyboardDismissAction()

    // The iOS number pad has no return key, so a price field can leave the keyboard up with the
    // Save and Cancel buttons behind it and no way to reach them. `imePadding` lifts the content
    // clear, `verticalScroll` keeps it reachable when the keyboard takes most of the sheet, and a
    // tap anywhere off a field puts the keyboard away.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advert_sold_sheet")
            .imePadding()
            .verticalScroll(rememberScrollState())
            .dismissKeyboardOnTapOutside(dismissKeyboard)
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        if (prompt.askingPrice) {
            SoldPriceStep(prompt = prompt, onPriceSubmitted = onPriceSubmitted)
        } else {
            SoldQuestionStep(prompt = prompt, onAnswer = onAnswer)
        }
    }
}

@Composable
private fun SoldQuestionStep(prompt: SoldPrompt, onAnswer: (Boolean) -> Unit) {
    Text(
        text = stringResource(Res.string.advert_sold_prompt_title),
        style = AppTheme.typography.headline,
        color = AppTheme.colors.onBackground,
    )
    Text(
        text = stringResource(Res.string.advert_sold_prompt_message),
        style = AppTheme.typography.body,
        color = AppTheme.colors.onSurfaceMuted,
    )
    if (!prompt.thenDelete) {
        // A take-down is not destructive and must not read as one. When this prompt is a step
        // inside deleting, the delete confirmation has already said the harsher thing and
        // repeating it here would only soften it.
        Text(
            text = stringResource(Res.string.advert_confirm_deactivate_message),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.onSurfaceMuted,
        )
    }
    AppButton(
        modifier = Modifier.fillMaxWidth().testTag("advert_sold_yes"),
        text = stringResource(Res.string.advert_sold_prompt_yes),
        enabled = !prompt.isSubmitting,
        onClick = { onAnswer(true) },
        style = AppButtonDefaults.success(),
    )
    AppButton(
        modifier = Modifier.fillMaxWidth().testTag("advert_sold_no"),
        // Not sold gets no follow-up questions: a seller closing a listing that failed does not
        // want a form. This button completes the whole flow.
        text = if (prompt.isSubmitting) {
            stringResource(Res.string.advert_action_in_progress)
        } else {
            stringResource(Res.string.advert_sold_prompt_no)
        },
        enabled = !prompt.isSubmitting,
        onClick = { onAnswer(false) },
        style = AppButtonDefaults.secondary(),
    )
}

@Composable
private fun SoldPriceStep(prompt: SoldPrompt, onPriceSubmitted: (Long?) -> Unit) {
    // Pre-filled with the asking price, since most things sell at or near it - so the common
    // answer is one tap, and the field behaves like every other price input in the app.
    val priceState = rememberTextFieldState(prompt.advert.priceValue?.toString().orEmpty())

    Text(
        text = stringResource(Res.string.advert_sold_price_title),
        style = AppTheme.typography.headline,
        color = AppTheme.colors.onBackground,
    )
    Text(
        text = stringResource(Res.string.advert_sold_price_message),
        style = AppTheme.typography.body,
        color = AppTheme.colors.onSurfaceMuted,
    )
    Text(
        text = stringResource(Res.string.advert_sold_price_label),
        style = AppTheme.typography.caption,
        color = AppTheme.colors.onSurfaceMuted,
    )
    TransparentInput(
        state = priceState,
        modifier = Modifier.testTag("advert_sold_price_input"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        inputTransformation = DigitOnlyInputTransformation,
        outputTransformation = ThousandSeparatorOutputTransformation,
    )
    AppButton(
        modifier = Modifier.fillMaxWidth().testTag("advert_sold_price_confirm"),
        text = if (prompt.isSubmitting) {
            stringResource(Res.string.advert_action_in_progress)
        } else {
            stringResource(Res.string.advert_sold_price_confirm)
        },
        enabled = !prompt.isSubmitting,
        onClick = { onPriceSubmitted(priceState.text.toString().toLongOrNull()) },
        style = AppButtonDefaults.success(),
    )
    AppButton(
        modifier = Modifier.fillMaxWidth().testTag("advert_sold_price_skip"),
        // Skipping is a complete answer, not a cancel: the sale is still recorded, and OLX still
        // gets its `is_success = true`. Only the price is left out.
        text = stringResource(Res.string.advert_sold_price_skip),
        enabled = !prompt.isSubmitting,
        onClick = { onPriceSubmitted(null) },
        style = AppButtonDefaults.ghost(),
    )
}

@PreviewLightDark
@Composable
private fun MarkAsSoldSheetPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            MarkAsSoldSheet(
                prompt = SoldPrompt(
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
                    thenDelete = false,
                    askingPrice = true,
                ),
                onAnswer = {},
                onPriceSubmitted = {},
            )
        }
    }
}
