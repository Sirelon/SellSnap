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

    const val AD_DESCRIPTION_REGENERATE_STARTED = "ad_description_regenerate_started"
    const val AD_DESCRIPTION_REGENERATE_SUCCEEDED = "ad_description_regenerate_succeeded"
    const val AD_DESCRIPTION_REGENERATE_FAILED = "ad_description_regenerate_failed"

    const val AD_GENERATED_CONTENT_VOTED = "ad_generated_content_voted"

    // Carries `field` (title | description | price | published_url), never the copied text itself.
    // For a guest - who cannot publish and is told by guest_copy_hint to copy the fields into OLX
    // by hand - this is the only success signal the funnel has.
    const val AD_CONTENT_COPIED = "ad_content_copied"

    // Which button the seller pressed on the "leave and lose your draft?" sheet, as `choice`
    // (stay | leave). The sheet's own screen_view only says it was shown; without this, a seller
    // who backs out and keeps editing is indistinguishable from one who abandons the draft.
    const val AD_DRAFT_EXIT_CHOICE = "ad_draft_exit_choice"

    // Ad lifecycle (SIR-106). Buckets and enums only: no prices in absolute terms, no advert
    // ids, no titles. Account identity is the localIndex convention, never anything identifying.

    /** `action` (deactivate | activate | finish | delete | extend | edit), `from_status`,
     * `result` (success | rejected | failed | partial). `rejected` is OLX refusing the action for
     * this advert's state and is the health signal for the status-to-action mapping in
     * `availableActions` - a pattern of it against one status means a seller is being offered a
     * button that cannot work. `partial` is only ever a delete whose deactivate half landed and
     * whose delete half did not. */
    const val ADVERT_ACTION = "advert_action"

    /** The one that matters: `price_delta_percent_bucket` is the gap between what the AI
     * suggested and what the item actually went for. Aggregated by category and country, it is a
     * direct read on whether the price suggestions are any good, which nothing else measures.
     * Also carries `days_live_bucket` and `had_price_entered`. */
    const val ADVERT_SOLD = "advert_sold"

    /** The other half of the outcome picture - carries `days_live_bucket` only. */
    const val ADVERT_CLOSED_UNSOLD = "advert_closed_unsold"

    /** `advert_views_bucket`, `had_zero_views`. Whether the diagnostic numbers get looked at. */
    const val ADVERT_STATISTICS_VIEWED = "advert_statistics_viewed"

    /** `fields_changed` (count), `was_price_only`. Answers whether a price-only quick edit is the
     * right primary path. */
    const val ADVERT_EDITED = "advert_edited"
}
