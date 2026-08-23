package com.sirelon.sellsnap.analytics

object AnalyticsEvents {
    const val AUTH_STARTED = "auth_started"
    const val AUTH_COMPLETED = "auth_completed"
    const val AUTH_FAILED = "auth_failed"

    const val ONBOARDING_COMPLETED = "onboarding_completed"

    const val AD_GENERATION_STARTED = "ad_generation_started"
    const val AD_GENERATION_SUCCEEDED = "ad_generation_succeeded"
    const val AD_GENERATION_FAILED = "ad_generation_failed"

    const val PHOTO_UPLOAD_FAILED = "photo_upload_failed"

    const val AD_PUBLISH_STARTED = "ad_publish_started"
    const val AD_PUBLISH_SUCCEEDED = "ad_publish_succeeded"
    const val AD_PUBLISH_FAILED = "ad_publish_failed"

    const val AD_DESCRIPTION_REGENERATE_STARTED = "ad_description_regenerate_started"
    const val AD_DESCRIPTION_REGENERATE_SUCCEEDED = "ad_description_regenerate_succeeded"
    const val AD_DESCRIPTION_REGENERATE_FAILED = "ad_description_regenerate_failed"

    const val AD_GENERATED_CONTENT_VOTED = "ad_generated_content_voted"
}
