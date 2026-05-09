package com.sirelon.sellsnap.designsystem.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import com.sirelon.sellsnap.designsystem.ChipData
import com.sirelon.sellsnap.designsystem.ChipStyle
import kotlin.random.Random

object RandomData {

    fun randomChip(
        label: String? = null,
        style: ChipStyle = ChipStyle.entries.random(),
    ): ChipData {
        val foodTags = listOf(
            "Protein", "Carbs", "Fats", "Fiber", "Vitamins", "Low Sugar",
            "High Protein", "Keto", "Vegan", "Gluten-Free", "Breakfast", "Snack",
            "Post-Workout", "Hydration", "Balanced", "Low Calorie"
        )

        val icons = listOf(
            Icons.Default.FitnessCenter,   // for protein, workouts
            Icons.Default.EnergySavingsLeaf, // for healthy/green
            Icons.Default.LocalDining,     // for general food
            Icons.Default.WaterDrop,       // for hydration
            Icons.Default.Favorite,        // for heart/healthy
            Icons.Default.Whatshot,        // for energy/fat burn
            Icons.Default.Eco,             // for natural/vegan
            null
        )

        val text = label ?: foodTags.random()
        val icon = icons.random()
        return ChipData(text = text, icon = icon, style = style)
    }


    fun randomQualityChip(): ChipData {
        val tags = listOf(
            "High quality",
            "Needs attention",
            "Balanced",
            "Uncertain",
            "Too fatty",
            "Too sweet",
            "Protein-rich",
            "Low energy"
        )

        val icons = listOf(
            Icons.Default.Star,           // quality
            Icons.Default.Warning,        // needs attention
            Icons.Default.Balance,        // balanced
            Icons.Default.Whatshot,       // too fatty
            Icons.Default.FavoriteBorder, // too sweet
            Icons.Default.FitnessCenter,  // protein
            Icons.Default.EnergySavingsLeaf, // low energy
            null
        )

        val index = tags.indices.random()
        val style = when (tags[index]) {
            "High quality",
            "Balanced",
            "Protein-rich" -> ChipStyle.Success

            "Needs attention",
            "Too fatty",
            "Too sweet" -> ChipStyle.Error

            else -> ChipStyle.Neutral
        }
        return ChipData(text = tags[index], icon = icons.getOrNull(index), style = style)
    }

    fun randomInsightChip(random: Random, average: Int): List<ChipData> {

        val options = listOf(
            ChipData(
                "Avg ${average} kcal/day",
                Icons.Default.LocalFireDepartment,
                ChipStyle.Success
            ),
            ChipData(
                "Consistency +${random.nextInt(2, 12)}%",
                Icons.Default.Insights,
                ChipStyle.Success
            ),
            ChipData(
                "Protein +${random.nextInt(5, 20)}g",
                Icons.Default.FitnessCenter,
                ChipStyle.Success
            ),
            ChipData(
                "Hydration ${random.nextInt(1, 4)}L",
                Icons.Default.WaterDrop,
                ChipStyle.Success
            ),
            ChipData("Sleep ${random.nextInt(6, 9)}h", Icons.Default.Bedtime, ChipStyle.Success),
            ChipData(
                "Steps ${random.nextInt(6000, 12000)}",
                Icons.Default.DirectionsRun,
                ChipStyle.Success
            ),
            ChipData("Fat −${random.nextInt(5, 15)}%", Icons.Default.Whatshot, ChipStyle.Success),
            ChipData(
                "Carbs ${random.nextInt(40, 65)}%",
                Icons.Default.LocalDining,
                ChipStyle.Success
            ),
        )

        return options.shuffled(random).take(2)
    }
}