package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val appNavigationSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(AppKey.Splash::class, AppKey.Splash.serializer())
                subclass(AppKey.SellerOnboarding::class, AppKey.SellerOnboarding.serializer())
                subclass(AppKey.ConsentPrompt::class, AppKey.ConsentPrompt.serializer())
                subclass(AppKey.SellerLanding::class, AppKey.SellerLanding.serializer())
                subclass(AppKey.OlxCountryPicker::class, AppKey.OlxCountryPicker.serializer())
                subclass(AppKey.DeleteAccountDataConfirm::class, AppKey.DeleteAccountDataConfirm.serializer())
                subclass(AppKey.AddOlxAccountConfirm::class, AppKey.AddOlxAccountConfirm.serializer())
                subclass(AppKey.OlxAccountAuthFailed::class, AppKey.OlxAccountAuthFailed.serializer())
                subclass(
                    AppKey.DisconnectOlxAccountConfirm::class,
                    AppKey.DisconnectOlxAccountConfirm.serializer(),
                )
                subclass(AppKey.GenerateAd::class, AppKey.GenerateAd.serializer())
                subclass(AppKey.MyAdverts::class, AppKey.MyAdverts.serializer())
                subclass(AppKey.Profile::class, AppKey.Profile.serializer())
                subclass(AppKey.Settings::class, AppKey.Settings.serializer())
                subclass(AppKey.WhatsNewPrompt::class, AppKey.WhatsNewPrompt.serializer())
                subclass(AppKey.AllReleases::class, AppKey.AllReleases.serializer())
                subclass(AppKey.SellerPublishSuccess::class, AppKey.SellerPublishSuccess.serializer())
                subclass(AppKey.ImagesPreview::class, AppKey.ImagesPreview.serializer())
                subclass(AppKey.PreviewAd::class, AppKey.PreviewAd.serializer())
                subclass(AppKey.SelectCategory::class, AppKey.SelectCategory.serializer())
                subclass(AppKey.PreviewBackInfo::class, AppKey.PreviewBackInfo.serializer())
                subclass(AppKey.PreviewPublishConfirm::class, AppKey.PreviewPublishConfirm.serializer())
                subclass(AppKey.PreviewAccountPicker::class, AppKey.PreviewAccountPicker.serializer())
            }
        }
    }
