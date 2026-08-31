package com.sirelon.sellsnap.features.seller.ad.preview_ad

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.domain.ValidationError
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlin.jvm.JvmInline

@Immutable
data class OlxAttributeState(
    val attribute: OlxAttribute,
    val selectedValues: List<OlxAttributeValue> = emptyList(),
    val error: ValidationError? = null,
)

/** Display-only projection of the active OLX account for the "Publish to" row (SIR-83 U6/D2).
 * Deliberately not the internal [com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord]
 * itself, so the UI layer only ever sees what it needs to render. */
@Immutable
data class PublishTargetAccount(
    val name: String,
    val avatarUrl: String?,
    val isBusiness: Boolean,
)

/** One row in the publish screen's account-picker sheet (SIR-83 U6). [email] disambiguates two
 * accounts sharing an OLX profile name (PRD §8). [needsReconnect] rows are shown but not
 * selectable here - actually reconnecting (OAuth) is Profile's flow, out of this screen's scope. */
@Immutable
data class PublishAccountPickerItem(
    val localIndex: Int,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val isBusiness: Boolean,
    val isActive: Boolean,
    val needsReconnect: Boolean,
)

enum class GeneratedContentVote { Up, Down }

interface PreviewAdContract {

    @Immutable
    data class PreviewAdState(
        val categoryLabel: String,
        val selectedCategory: OlxCategory? = null,
        val isPublishing: Boolean = false,
        val generationElapsedMs: Long = 0L,
        val isSessionResolved: Boolean = false,
        val price: Float,
        val minPrice: Float,
        val maxPrice: Float,
        val currency: OlxCurrency = OlxCurrency.Default,
        val images: List<String>,
        val location: OlxLocation? = null,
        val locationLoading: Boolean = false,
        val attributeItems: List<OlxAttributeState> = emptyList(),
        val isGuest: Boolean = false,
        // SIR-83: the account new listings will publish to. Shown only when there is a real
        // decision to make - i.e. screenshotMode (placeholder identity) or 2+ connected accounts
        // for the active country - so a single-account seller sees no new UI at all (PRD Q20).
        val targetAccount: PublishTargetAccount? = null,
        val showTargetAccountRow: Boolean = false,
        val accountPickerItems: List<PublishAccountPickerItem> = emptyList(),
        val isRegeneratingDescription: Boolean = false,
        val regenerationCount: Int = 0,
        val selectedVote: GeneratedContentVote? = null,
        val currentAttemptId: String? = null,
    )

    sealed interface PreviewAdEvent {
        data class CategorySelected(val category: OlxCategory) : PreviewAdEvent

        data object OnChangeCategoryClick : PreviewAdEvent
        data object Publish : PreviewAdEvent

        @JvmInline
        value class OnPriceChanged(val price: Float) : PreviewAdEvent

        data object FetchLocation : PreviewAdEvent
        data object RefreshLocationClicked : PreviewAdEvent
        data object RegenerateDescription : PreviewAdEvent

        data class AttributeValueChanged(
            val attributeCode: String,
            val values: List<OlxAttributeValue>,
        ) : PreviewAdEvent

        /** Seller picked another account from the publish screen's account picker (SIR-83 U6) -
         * switches the active account; the draft is not touched (D2). */
        data class SwitchAccountRequested(val localIndex: Int) : PreviewAdEvent

        data class VoteGeneratedContent(val vote: GeneratedContentVote) : PreviewAdEvent
    }

    sealed interface PreviewAdEffect {
        data class ShowMessage(val message: String) : PreviewAdEffect
        data object GoToGategoryPicker : PreviewAdEffect
        data class PublishSuccess(val data: PublishSuccessData) : PreviewAdEffect
        data class PublishFailure(val message: String) : PreviewAdEffect
        data class NavigateToProfile(val reason: String) : PreviewAdEffect

        /** D6/A5: the token about to publish did not belong to the account shown on this screen
         * (a missed [com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository.setActiveAccount]
         * call site, or a switch mid-flight). Nothing was posted. Distinct from [PublishFailure]
         * so the seller understands this is an account-mismatch, not a generic API error. */
        data class PublishAccountMismatch(val message: String) : PreviewAdEffect

        /** The active account's token is dead (terminal refresh failure surfacing as
         * [com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError.InvalidGrant]/[com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError.InvalidToken]).
         * [actionLabel] names the account so the UI can offer an inline "Reconnect <account>"
         * action that navigates to Profile - it must never auto-launch OAuth from here. */
        data class PublishNeedsReconnect(val message: String, val actionLabel: String) : PreviewAdEffect
    }
}
