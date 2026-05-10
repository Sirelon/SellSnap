package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun OlxAuthWebView(
    url: String,
    redirectUri: String,
    onUrlIntercepted: (String) -> Unit,
    modifier: Modifier = Modifier,
)
