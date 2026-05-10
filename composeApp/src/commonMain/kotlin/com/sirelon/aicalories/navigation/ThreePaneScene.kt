package com.sirelon.sellsnap.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.sirelon.sellsnap.designsystem.AppTheme

class ThreePaneScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val firstEntry: NavEntry<T>,
    val secondEntry: NavEntry<T>,
    val thirdEntry: NavEntry<T>?,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOfNotNull(firstEntry, secondEntry, thirdEntry)

    override val content: @Composable (() -> Unit) = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
                .animateContentSize(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                firstEntry.Content()
            }
            Column(modifier = Modifier.weight(1f)) {
                secondEntry.Content()
            }

            thirdEntry?.let {
                Column(modifier = Modifier.weight(1f)) {
                    thirdEntry.Content()
                }
            }
        }
    }
}

@Composable
fun <T : Any> rememberThreePaneSceneStrategy(): ThreePaneSceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    return remember(windowSizeClass) {
        ThreePaneSceneStrategy(windowSizeClass)
    }
}

class ThreePaneSceneStrategy<T : Any>(val windowSizeClass: WindowSizeClass) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {

        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }

        val thirdEntry = entries.lastOrNull()?.takeIf { it.metadata.containsKey(THIRD_KEY) }
        val secondEntry = entries.findLast { it.metadata.containsKey(SECOND_KEY) } ?: return null
        val firstEntry = entries.findLast { it.metadata.containsKey(FIRST_KEY) } ?: return null

        // We use the list's contentKey to uniquely identify the scene.
        // This allows the detail panes to be displayed instantly through recomposition, rather than
        // having NavDisplay animate the whole scene out when the selected detail item changes.
        val sceneKey = firstEntry.contentKey

        return ThreePaneScene(
            key = sceneKey,
            previousEntries = entries.dropLast(1),
            thirdEntry = thirdEntry,
            secondEntry = secondEntry,
            firstEntry = firstEntry,
        )
    }

    companion object {
        internal const val FIRST_KEY = "ThreePaneSceneStrategy-First"
        internal const val SECOND_KEY = "ThreePaneSceneStrategy-Second"

        internal const val THIRD_KEY = "ThreePaneSceneStrategy-Third"

        fun firstPane() = mapOf(FIRST_KEY to true)

        fun secondPane() = mapOf(SECOND_KEY to true)

        fun thirdPane() = mapOf(THIRD_KEY to true)
    }
}