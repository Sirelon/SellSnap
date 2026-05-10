package com.sirelon.sellsnap.features.seller.ad

import androidx.compose.runtime.Composable
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.time_duration_minutes
import com.sirelon.sellsnap.generated.resources.time_duration_minutes_seconds
import com.sirelon.sellsnap.generated.resources.time_duration_seconds
import com.sirelon.sellsnap.generated.resources.time_unit_minute_one
import com.sirelon.sellsnap.generated.resources.time_unit_minute_other
import com.sirelon.sellsnap.generated.resources.time_unit_second_one
import com.sirelon.sellsnap.generated.resources.time_unit_second_other
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun formatFriendlyElapsedTime(elapsedMs: Long): String {
    val totalSeconds = (((elapsedMs.coerceAtLeast(0L)) + 999L) / 1000L).coerceAtLeast(1L)
    val minutes = (totalSeconds / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()

    if (minutes == 0) {
        return stringResource(
            Res.string.time_duration_seconds,
            totalSeconds.toInt(),
            durationUnit(
                value = totalSeconds.toInt(),
                one = Res.string.time_unit_second_one,
                other = Res.string.time_unit_second_other,
            ),
        )
    }

    if (seconds == 0) {
        return stringResource(
            Res.string.time_duration_minutes,
            minutes,
            durationUnit(
                value = minutes,
                one = Res.string.time_unit_minute_one,
                other = Res.string.time_unit_minute_other,
            ),
        )
    }

    return stringResource(
        Res.string.time_duration_minutes_seconds,
        minutes,
        durationUnit(
            value = minutes,
            one = Res.string.time_unit_minute_one,
            other = Res.string.time_unit_minute_other,
        ),
        seconds,
        durationUnit(
            value = seconds,
            one = Res.string.time_unit_second_one,
            other = Res.string.time_unit_second_other,
        ),
    )
}

@Composable
private fun durationUnit(
    value: Int,
    one: StringResource,
    other: StringResource,
): String = stringResource(if (value == 1) one else other)
