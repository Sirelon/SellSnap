package com.sirelon.sellsnap.config

import com.sirelon.sellsnap.supabase.SupabaseConfig

object AppConfig {
    val olxClientId: String
        get() = SupabaseConfig.OLX_CLIENT_ID

    val olxClientSecret: String
        get() = SupabaseConfig.OLX_CLIENT_SECRET

    val olxScope: String
        get() = SupabaseConfig.OLX_SCOPE

    val olxAuthBaseUrl: String
        get() = SupabaseConfig.OLX_AUTH_BASE_URL

    val olxRedirectUri: String
        get() = SupabaseConfig.OLX_REDIRECT_URI
}
