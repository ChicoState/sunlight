package edu.sunlight.screentime

import java.time.LocalTime

private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_HOUR = 60

internal fun normalizeSunlightTimes(times: Iterable<LocalTime>): List<LocalTime> =
    times.distinct().sorted()

internal fun encodeSunlightTimes(times: Iterable<LocalTime>): Set<String> =
    normalizeSunlightTimes(times)
        .map { time -> (time.hour * MINUTES_PER_HOUR + time.minute).toString() }
        .toSet()

internal fun decodeSunlightTimes(values: Set<String>): List<LocalTime> =
    normalizeSunlightTimes(
        values.mapNotNull { value ->
          value
              .toIntOrNull()
              ?.takeIf { it in 0 until MINUTES_PER_DAY }
              ?.let { minutes ->
                LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
              }
        }
    )
