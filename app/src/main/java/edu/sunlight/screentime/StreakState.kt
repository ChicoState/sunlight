package edu.sunlight.screentime

import java.time.LocalDate

/** Durable and displayable summary of the user's morning-routine streak. */
internal data class StreakState(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletedDate: LocalDate? = null,
)
