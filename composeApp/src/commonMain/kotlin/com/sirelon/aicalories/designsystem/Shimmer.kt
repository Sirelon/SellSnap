package com.sirelon.sellsnap.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

private const val ShimmerSweepMillis = 1200

/**
 * While [visible], a soft diagonal band of [color] sweeps left to right over the content, inside
 * [shape]: the content is being worked on. The content stays readable and laid out as it is.
 */
fun Modifier.shimmer(
    visible: Boolean,
    color: Color,
    shape: Shape = RectangleShape,
): Modifier = if (visible) this then ShimmerElement(color, shape) else this

private data class ShimmerElement(val color: Color, val shape: Shape) : ModifierNodeElement<ShimmerNode>() {
    override fun create() = ShimmerNode(color, shape)

    override fun update(node: ShimmerNode) {
        node.color = color
        node.shape = shape
        node.invalidateDraw()
    }
}

private class ShimmerNode(var color: Color, var shape: Shape) : Modifier.Node(), DrawModifierNode {
    private val progress = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(ShimmerSweepMillis, easing = LinearEasing), RepeatMode.Restart),
            )
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val bandWidth = size.width / 2f
        // From fully off the left edge to fully off the right one.
        val center = -bandWidth + (size.width + 2f * bandWidth) * progress.value
        val brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, color, Color.Transparent),
            start = Offset(center - bandWidth / 2f, 0f),
            end = Offset(center + bandWidth / 2f, size.height),
        )
        drawOutline(shape.createOutline(size, layoutDirection, this), brush)
    }
}
