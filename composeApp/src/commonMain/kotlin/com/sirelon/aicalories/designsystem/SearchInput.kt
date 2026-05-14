package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_x
import com.sirelon.sellsnap.generated.resources.keyboard_done_action
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.typography
    val dismissKeyboard = rememberKeyboardDismissAction()
    val platformImeOptions = rememberPlatformImeOptions(
        doneLabel = stringResource(Res.string.keyboard_done_action),
        onDone = dismissKeyboard,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surfaceLow, RoundedCornerShape(AppDimens.BorderRadius.xl))
            .padding(horizontal = AppDimens.Spacing.xl, vertical = AppDimens.Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = c.onSurfaceMuted,
            modifier = Modifier.size(AppDimens.Size.xl3),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                platformImeOptions = platformImeOptions,
            ),
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
            textStyle = t.body.copy(color = c.onSurface),
            cursorBrush = SolidColor(c.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = t.body, color = c.onSurfaceMuted)
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(AppDimens.Size.xl5),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_x),
                    contentDescription = null,
                    tint = c.onSurfaceMuted,
                    modifier = Modifier.size(AppDimens.Size.xl2),
                )
            }
        }
    }
}
