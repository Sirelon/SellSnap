package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable

@Composable
expect fun rememberOlxAuthLauncher(): (String) -> Unit
