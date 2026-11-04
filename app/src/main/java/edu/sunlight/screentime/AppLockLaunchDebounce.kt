package edu.sunlight.screentime

private const val BLOCK_DEBOUNCE_MILLIS = 750L

/** Repeated launches must wait, rather than dropping a locked app's last foreground event. */
internal class AppLockLaunchDebounce {
  private var lastPackage: String? = null
  private var lastLaunchAt = 0L

  fun remainingMillis(packageName: String, elapsedMillis: Long): Long =
      if (packageName == lastPackage) {
        (BLOCK_DEBOUNCE_MILLIS - (elapsedMillis - lastLaunchAt)).coerceAtLeast(0L)
      } else {
        0L
      }

  fun launched(packageName: String, elapsedMillis: Long) {
    lastPackage = packageName
    lastLaunchAt = elapsedMillis
  }
}
