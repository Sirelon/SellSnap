package com.sirelon.sellsnap.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        // A phone-width sheet floats in the middle of a tablet or desktop window, so from the
        // medium width class up the same entry renders as a centred dialog.
        val isWideWindow = currentWindowAdaptiveInfoV2().windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

        if (isWideWindow) {
            BasicAlertDialog(
                onDismissRequest = onBack,
                properties = DialogProperties(
                    dismissOnBackPress = modalBottomSheetProperties.shouldDismissOnBackPress,
                    dismissOnClickOutside = modalBottomSheetProperties.shouldDismissOnClickOutside,
                ),
            ) {
                Surface(
                    shape = RoundedCornerShape(AppDimens.BorderRadius.xl7),
                    color = AppTheme.colors.surface,
                ) {
                    // The sheet content starts below a drag handle; the dialog has none.
                    Box(modifier = Modifier.padding(top = AppDimens.Spacing.xl5)) {
                        CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                            entry.Content()
                        }
                    }
                }
            }
        } else {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = onBack,
                sheetState = sheetState,
                containerColor = AppTheme.colors.surface,
                properties = modalBottomSheetProperties,
            ) {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    entry.Content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val bottomSheetProperties = lastEntry.metadata[BottomSheetKey] ?: return null

        return BottomSheetScene(
            key = lastEntry.contentKey,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            modalBottomSheetProperties = bottomSheetProperties,
            onBack = onBack,
        )
    }

    companion object {
        object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>

        fun bottomSheet(
            modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
        ): Map<String, Any> = metadata {
            put(BottomSheetKey, modalBottomSheetProperties)
        }
    }
}
