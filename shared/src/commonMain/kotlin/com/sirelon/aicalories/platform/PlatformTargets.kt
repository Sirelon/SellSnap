package com.sirelon.sellsnap.platform

import io.github.jan.supabase.CurrentPlatformTarget
import io.github.jan.supabase.PlatformTarget

/**
 * Central place for runtime platform checks based on Supabase's [CurrentPlatformTarget].
 */
object PlatformTargets {

    val current: PlatformTarget
        get() = CurrentPlatformTarget

    fun isIos(): Boolean = current == PlatformTarget.IOS

    fun isAndroid(): Boolean = current == PlatformTarget.ANDROID

    fun isMobile(): Boolean = isAndroid() || isIos()

    fun isWeb(): Boolean = current == PlatformTarget.JS || current == PlatformTarget.WASM_JS

    fun isDesktop(): Boolean = current in desktopTargets

    private val desktopTargets = setOf(
        PlatformTarget.JVM,
        PlatformTarget.LINUX,
        PlatformTarget.MACOS,
        PlatformTarget.WINDOWS,
    )
}
