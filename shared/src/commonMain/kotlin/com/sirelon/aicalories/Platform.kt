package com.sirelon.sellsnap

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform