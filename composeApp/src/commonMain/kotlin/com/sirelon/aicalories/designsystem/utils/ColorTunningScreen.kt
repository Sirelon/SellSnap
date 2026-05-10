package com.sirelon.sellsnap.designsystem.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun ColorTuningScreen() {
    var minS by remember { mutableStateOf(0.35f) }
    var maxS by remember { mutableStateOf(0.7f) }
    var minV by remember { mutableStateOf(0.75f) }
    var maxV by remember { mutableStateOf(0.95f) }

    var color by remember { mutableStateOf(generateColor(minS, maxS, minV, maxV)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // 🎨 Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Preview",
                color = color.contrastColor()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Controls
        Text("Saturation min: ${"%.2f".format(minS)}")
        Slider(value = minS, onValueChange = {
            minS = it
            color = generateColor(minS, maxS, minV, maxV)
        })

        Text("Saturation max: ${"%.2f".format(maxS)}")
        Slider(value = maxS, onValueChange = {
            maxS = it
            color = generateColor(minS, maxS, minV, maxV)
        })

        Text("Brightness min: ${"%.2f".format(minV)}")
        Slider(value = minV, onValueChange = {
            minV = it
            color = generateColor(minS, maxS, minV, maxV)
        })

        Text("Brightness max: ${"%.2f".format(maxV)}")
        Slider(value = maxV, onValueChange = {
            maxV = it
            color = generateColor(minS, maxS, minV, maxV)
        })

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            color = generateColor(minS, maxS, minV, maxV)
        }) {
            Text("Generate new color")
        }
    }
}

// --- Generator using your tunable params ---

fun generateColor(
    minS: Float,
    maxS: Float,
    minV: Float,
    maxV: Float
): Color {
    val h = Random.nextFloat() * 360f
    val s = minS + Random.nextFloat() * (maxS - minS)
    val v = minV + Random.nextFloat() * (maxV - minV)

    return hsvToColor(h, s, v)
}

fun String.format(value: Float): String {
    val regex = Regex("%\\.(\\d+)f")
    val match = regex.find(this) ?: return value.toString()

    val decimals = match.groupValues[1].toInt()

    val factor = 10.0.pow(decimals).toInt()
    val rounded = (value * factor).roundToInt() / factor.toFloat()

    val formatted = if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }

    return this.replace(match.value, formatted)
}