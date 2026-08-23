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

    // Multi-account (SIR-83). No event may carry an email, OLX user id, account name, or token -
    // only localIndex/counts, per PRD §11.
    const val ACCOUNT_SWITCHED = "account_switched"
    const val ACCOUNT_ADD_STARTED = "account_add_started"
    const val ACCOUNT_ADD_COMPLETED = "account_add_completed"
    const val ACCOUNT_ADD_FAILED = "account_add_failed"
    const val ACCOUNT_DISCONNECTED = "account_disconnected"
    const val ACCOUNT_TOKEN_EXPIRED_UNUSED = "account_token_expired_unused"

    // Fired by PreviewAdViewModel.publishAdvert() when the token about to be used does not
    // belong to the account named on the preview row (D6/A5) - the primary defence for G4
    // (publishing to the wrong OLX account). Must be zero in the wild.
    const val PUBLISH_ACCOUNT_MISMATCH_ABORTED = "publish_account_mismatch_aborted"
}
