package com.sirelon.sellsnap.features.announcements.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.features.announcements.model.Announcement
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.announcement_close
import com.sirelon.sellsnap.generated.resources.announcement_ok
import com.sirelon.sellsnap.generated.resources.ic_x
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Exact value from the spec; no AppDimens token sits at 180.
private val ImageMaxHeight = 180.dp

/**
 * Not a nav entry: a restored back stack after process death would resurrect an empty,
 * non-dismissible dialog, so it is rendered from `AnnouncementViewModel` state at the App level
 * instead.
 *
 * [onDismiss] runs for the close button, OK, and (only when the announcement is dismissible)
 * back or a tap outside.
 */
@Composable
fun AnnouncementDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = announcement.dismissible,
            dismissOnClickOutside = announcement.dismissible,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.BorderRadius.xl7),
            color = AppTheme.colors.surface,
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.Spacing.xl5),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = announcement.title,
                        style = AppTheme.typography.headline,
                        color = AppTheme.colors.onBackground,
                        modifier = Modifier.weight(1f).padding(top = AppDimens.Spacing.m),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_x),
                            contentDescription = stringResource(Res.string.announcement_close),
                            tint = AppTheme.colors.onSurfaceMuted,
                            modifier = Modifier.size(AppDimens.Size.xl6),
                        )
                    }
                }

                // Image and body scroll together; the OK button stays outside so a long body can
                // never push it off screen.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
                ) {
                    announcement.imageUrl?.let { imageUrl ->
                        AnnouncementImage(imageUrl)
                    }
                    announcement.body?.let { body ->
                        Text(
                            text = body,
                            style = AppTheme.typography.body,
                            color = AppTheme.colors.onSurface,
                        )
                    }
                }

                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.announcement_ok),
                    onClick = onDismiss,
                )
            }
        }
    }
}

// The dialog never waits on the image: it is shown at once and the picture appears when it loads.
// A failed load removes the image entirely so no blank gap is left above the text.
@Composable
private fun AnnouncementImage(imageUrl: String) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ImageMaxHeight)
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl3)),
    )
}
