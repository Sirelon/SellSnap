package com.sirelon.sellsnap.designsystem.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sirelon.sellsnap.common.MeasureUnit
import com.sirelon.sellsnap.common.shortName
import com.sirelon.sellsnap.designsystem.AppTheme

@Immutable
data class MacroStats(
    val calories: NutritionValue,
    val protein: NutritionValue,
    val carbs: NutritionValue,
    val fat: NutritionValue,
)

@Immutable
data class NutritionValue(
    val value: Double,
    val type: MeasureUnit,
)

@Composable
fun NutrientStat(value: NutritionValue) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.value.toString(),
            style = AppTheme.typography.title,
        )
        Text(
            text = value.type.shortName(),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
fun MacronutrientRow(stats: MacroStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {

        NutrientStat(value = stats.calories)
        NutrientStat(value = stats.protein)
        NutrientStat(value = stats.carbs)
        NutrientStat(value = stats.fat)
    }
}
