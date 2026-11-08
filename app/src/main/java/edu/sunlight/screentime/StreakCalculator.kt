package edu.sunlight.screentime

import java.time.LocalDate

/**
 * Pure streak rules. Keep this class independent of Android and DataStore for easy unit testing.
 */
internal object StreakCalculator {
  /** Records one eligible completion and returns the resulting state. */
  fun recordCompletion(existing: StreakState, completionDate: LocalDate): StreakState {
    val lastDate = existing.lastCompletedDate

    // A routine can be observed more than once, and older dates must not change the saved streak.
    if (lastDate != null && !completionDate.isAfter(lastDate)) {
      return existing
    }

    val newCurrentStreak =
        when {
          lastDate == null -> 1
          completionDate == lastDate.plusDays(1) -> existing.currentStreak + 1
          else -> 1
        }

    return existing.copy(
        currentStreak = newCurrentStreak,
        longestStreak = maxOf(existing.longestStreak, newCurrentStreak),
        lastCompletedDate = completionDate,
    )
  }

  /** Returns the active streak to display for [today]. */
  fun currentStreakForToday(existing: StreakState, today: LocalDate): Int {
    val lastDate = existing.lastCompletedDate ?: return 0

    return if (lastDate == today || lastDate == today.minusDays(1)) {
      existing.currentStreak
    } else {
      0
    }
  }
}
