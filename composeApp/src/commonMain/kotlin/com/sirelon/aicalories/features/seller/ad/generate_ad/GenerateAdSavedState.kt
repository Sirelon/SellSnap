package com.sirelon.sellsnap.features.seller.ad.generate_ad

import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import kotlinx.serialization.Serializable

@Serializable
data class GenerateAdSavedState(
    val prompt: String = "",
    val photos: List<DraftPhoto> = emptyList(),
)

/**
 * The New listing input for the life of the process (SIR-188). A tab switch rebuilds the back stack,
 * which drops the GenerateAd entry with its ViewModel and SavedStateHandle; the next
 * [GenerateAdViewModel] restores the photos and the note from here. The SavedStateHandle still
 * covers process death, when this store is empty.
 */
class GenerateAdInputStore {
    var savedState: GenerateAdSavedState? = null

    fun clear() {
        savedState = null
    }
}
