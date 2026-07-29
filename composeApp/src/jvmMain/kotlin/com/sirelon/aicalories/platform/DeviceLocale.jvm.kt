package com.sirelon.sellsnap.platform

import java.util.Locale

actual fun getDeviceCountryCode(): String? = Locale.getDefault().country.lowercase().ifBlank { null }
