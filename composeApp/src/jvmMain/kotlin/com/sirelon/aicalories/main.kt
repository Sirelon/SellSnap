package com.sirelon.sellsnap

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SellSnap",
    ) {
        App()
    }
}