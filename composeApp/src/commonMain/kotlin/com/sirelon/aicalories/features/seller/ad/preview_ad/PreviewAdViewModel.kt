package com.sirelon.sellsnap.features.seller.ad.preview_ad

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.designsystem.formatPrice
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.ad.data.PostAdvertRequestMapper
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEffect
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEffect.ShowMessage
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent.CategorySelected
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent.FetchLocation
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent.RefreshLocationClicked
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent.Publish
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdState
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeInputType
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeValidationResult
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeValidator
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_attributes_load_failed
import com.sirelon.sellsnap.generated.resources.error_category_suggestion_failed
import com.sirelon.sellsnap.generated.resources.error_location_fetch_failed
import com.sirelon.sellsnap.generated.resources.error_publish_failed
import com.sirelon.sellsnap.generated.resources.error_publish_missing_category_or_location
import com.sirelon.sellsnap.generated.resources.error_publish_missing_contact_name
import com.sirelon.sellsnap.generated.resources.validation_error_desc_too_short
import com.sirelon.sellsnap.generated.resources.validation_error_title_too_short
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

private const val TitleMinLength = 10
private const val DescriptionMinLength = 30

class PreviewAdViewModel(
    private val filledAdvertisement: AdvertisementWithAttributes,
    private val categoriesRepository: CategoriesRepository,
    private val locationRepository: LocationRepository,
    private val olxApiClient: OlxApiClient,
    private val attributeValidator: AttributeValidator,
    private val authRepository: OlxAuthRepository,
    private val adFlowTimerStore: AdFlowTimerStore,
) : BaseViewModel<PreviewAdState, PreviewAdEvent, PreviewAdEffect>() {

    private val advertisement = filledAdvertisement.advertisement

    val titleState = TextFieldState(advertisement.title)
    val descriptionState = TextFieldState(advertisement.description)

    private val selectedCategoryId = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val savedLocation = locationRepository.getSavedLocation()
            if (savedLocation != null) {
                setState { it.copy(location = savedLocation) }
            }

            val isGuest = authRepository.currentSession().mode == SellerSessionMode.Guest
            setState { it.copy(isGuest = isGuest, isSessionResolved = true) }
            if (isGuest) return@launch

            snapshotFlow { titleState.text }
                .distinctUntilChanged()
                .debounce(300L)
                .flatMapLatest {
                    categoriesRepository.categorySuggestion(it.toString())
                }
                .onEach {
                    updateSelectedCategory(category = it)
                }
                .flatMapLatest {
                    categoriesRepository.getAttributes(it.id)
                }
                .onEach { attributes ->
                    setState { it.copy(attributes = attributes) }
                }
                .catch {
                    postEffect(ShowMessage(it.message ?: getString(Res.string.error_category_suggestion_failed)))
                }
                .launchIn(viewModelScope)

            selectedCategoryId
                .filterNotNull()
                .flatMapLatest { categoryId ->
                    categoriesRepository.getAttributes(categoryId)
                }
                .onEach { attributes ->
                    setState {
                        it.copy(
                            attributeItems = attributes.map { attribute ->
                                OlxAttributeState(
                                    attribute = attribute,
                                    selectedValues = filledAdvertisement.filledAttributes[attribute.code].orEmpty(),
                                )
                            }
                        )
                    }
                }
                .catch {
                    setState { state -> state.copy(attributeItems = emptyList()) }
                    postEffect(ShowMessage(it.message ?: getString(Res.string.error_attributes_load_failed)))
                }
                .launchIn(viewModelScope)
        }
    }

    override fun initialState() = PreviewAdState(
        categoryLabel = "",
        generationElapsedMs = adFlowTimerStore.generationElapsedMs(),
        price = advertisement.suggestedPrice,
        minPrice = advertisement.minPrice,
        maxPrice = advertisement.maxPrice,
        images = advertisement.images,
    )

    override fun onEvent(event: PreviewAdEvent) {
        when (event) {
            is CategorySelected -> viewModelScope.launch {
                updateSelectedCategory(event.category)
            }

            is PreviewAdEvent.OnPriceChanged -> {
                setState {
                    it.copy(
                        price = event.price,
                        maxPrice = it.maxPrice.coerceAtLeast(event.price),
                        minPrice = it.minPrice.coerceAtMost(event.price),
                    )
                }
            }

            FetchLocation -> viewModelScope.launch {
                fetchUserLocation()
            }

            RefreshLocationClicked -> viewModelScope.launch {
                fetchUserLocation()
            }

            PreviewAdEvent.OnChangeCategoryClick -> postEffect(PreviewAdEffect.GoToGategoryPicker)

            Publish -> {
                viewModelScope.launch { publishAdvert() }
            }

            is PreviewAdEvent.AttributeValueChanged -> setState { currentState ->
                val index =
                    currentState.attributeItems.indexOfFirst { it.attribute.code == event.attributeCode }
                if (index == -1) return@setState currentState
                val item = currentState.attributeItems[index]
                val valuesToValidate = when (item.attribute.inputType) {
                    AttributeInputType.SingleSelect, AttributeInputType.MultiSelect ->
                        event.values.map { it.code }

                    AttributeInputType.NumericInput, AttributeInputType.TextInput ->
                        event.values.map { it.label }
                }
                val error = (
                    attributeValidator.validate(item.attribute, valuesToValidate) as? AttributeValidationResult.Invalid
                    )?.reason
                val updatedItems = currentState.attributeItems.toMutableList()
                updatedItems[index] = item.copy(selectedValues = event.values, error = error)
                currentState.copy(attributeItems = updatedItems)
            }
        }
    }

    private suspend fun fetchUserLocation() {
        setState { it.copy(locationLoading = true) }
        try {
            val location = locationRepository.fetchUserLocation()
            setState { it.copy(location = location, locationLoading = false) }
        } catch (e: Exception) {
            setState { it.copy(locationLoading = false) }
            postEffect(ShowMessage(e.message ?: getString(Res.string.error_location_fetch_failed)))
        }
    }

    private suspend fun publishAdvert() {
        val s = currentState()

        val title = titleState.text.toString()
        val description = descriptionState.text.toString()
        if (title.trim().length < TitleMinLength) {
            postEffect(ShowMessage(getString(Res.string.validation_error_title_too_short)))
            return
        }
        if (description.trim().length < DescriptionMinLength) {
            postEffect(ShowMessage(getString(Res.string.validation_error_desc_too_short)))
            return
        }

        val category = s.selectedCategory
        val location = s.location
        if (category == null || location == null) {
            postEffect(ShowMessage(getString(Res.string.error_publish_missing_category_or_location)))
            return
        }

        val validatedItems = s.attributeItems.map { item ->
            val valuesToValidate = when (item.attribute.inputType) {
                AttributeInputType.SingleSelect, AttributeInputType.MultiSelect ->
                    item.selectedValues.map { it.code }

                AttributeInputType.NumericInput, AttributeInputType.TextInput ->
                    item.selectedValues.map { it.label }
            }
            val error = (
                attributeValidator.validate(item.attribute, valuesToValidate) as? AttributeValidationResult.Invalid
                )?.reason
            item.copy(error = error)
        }
        val hasErrors = validatedItems.any { it.error != null }
        if (hasErrors) {
            setState { it.copy(attributeItems = validatedItems) }
            postEffect(ShowMessage(getString(Res.string.error_publish_failed)))
            return
        }

        postEffect(PreviewAdEffect.NavigateToPublishing)

        try {
            val contactName = olxApiClient.getAuthenticatedUser().name
            if (contactName.isBlank()) {
                postEffect(PreviewAdEffect.NavigateToProfile(getString(Res.string.error_publish_missing_contact_name)))
                return
            }
            val request = PostAdvertRequestMapper.map(
                title = title,
                description = description,
                category = category,
                location = location,
                images = s.images,
                price = s.price,
                contactName = contactName,
                attributeItems = validatedItems,
            )
            val data = olxApiClient.postAdvert(request)
            val successData = PublishSuccessData(
                url = data.url.orEmpty(),
                title = title,
                priceFormatted = "₴ ${formatPrice(s.price)}",
                primaryImageUrl = s.images.firstOrNull(),
                totalElapsedMs = adFlowTimerStore.totalElapsedMs(),
                status = data.status,
            )
            postEffect(PreviewAdEffect.PublishSuccess(successData))
        } catch (error: Throwable) {
            val olxError = (error as? OlxApiException)?.error
            if (olxError is OlxApiError.ValidationError && olxError.field.startsWith("contact.")) {
                postEffect(PreviewAdEffect.NavigateToProfile(olxError.userMessage))
            } else {
                val failureMessage = error.message ?: getString(Res.string.error_publish_failed)
                postEffect(PreviewAdEffect.PublishFailure(failureMessage))
            }
        }
    }

    private suspend fun updateSelectedCategory(category: OlxCategory) {
        val path = mutableListOf(category.label)
        var parentId = category.parentId
        while (parentId != null) {
            val parent = categoriesRepository.getCategoryById(parentId) ?: break
            path.add(0, parent.label)
            parentId = parent.parentId
        }
        setState {
            it.copy(
                categoryLabel = path.joinToString(" / "),
                selectedCategory = category,
                attributeItems = emptyList(),
            )
        }
        selectedCategoryId.value = category.id
    }
}
