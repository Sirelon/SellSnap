package com.sirelon.sellsnap

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    installKotlinCrashHandler()
    return ComposeUIViewController(
        configure = {
            onFocusBehavior = OnFocusBehavior.DoNothing
        },
    ) {
        App()
    }
}

// Route uncaught Kotlin exceptions to Crashlytics with their Kotlin stack trace; the native
// signal handler alone would only capture an opaque abort. recordException respects the runtime
// collection flag, so nothing is sent until analytics consent is granted.
@OptIn(ExperimentalNativeApi::class)
private fun installKotlinCrashHandler() {
    setUnhandledExceptionHook { throwable ->
        runCatching { Firebase.crashlytics.recordException(throwable) }
        // Keep normal crash behaviour: still terminate as the process otherwise would.
        terminateWithUnhandledException(throwable)
    }
}
