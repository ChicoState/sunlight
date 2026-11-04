package edu.sunlight.screentime

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detects which app comes to the foreground and covers locked apps with [AppBlockedActivity].
 *
 * It subscribes only to window-state changes and reads only the event's package name; it cannot
 * retrieve window content. Nothing is stored except the lock settings the user chose.
 */
class AppLockAccessibilityService : AccessibilityService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var settings: AppLockSettings? = null
  private var protectedPackages: Set<String> = emptySet()
  private var foregroundPackage: String? = null
  private val launchDebounce = AppLockLaunchDebounce()
  private var pendingRecheck: Job? = null
  private val screenReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = recheck()
      }

  override fun onServiceConnected() {
    running = WeakReference(this)
    protectedPackages = resolveProtectedPackages(this)
    ContextCompat.registerReceiver(
        this,
        screenReceiver,
        IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_ON) },
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    scope.launch {
      appLockRepository.settings.collect {
        settings = it
        protectedPackages = resolveProtectedPackages(this@AppLockAccessibilityService)
        AppLockScheduler.schedule(this@AppLockAccessibilityService, it, Instant.now())
        recheck()
      }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val packageName = event.packageName?.toString() ?: return
    foregroundPackage = packageName
    blockIfLocked(packageName)
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    if (running?.get() === this) running = null
    runCatching { unregisterReceiver(screenReceiver) }
    scope.cancel()
    super.onDestroy()
  }

  private fun recheck() {
    foregroundPackage?.let(::blockIfLocked)
  }

  private fun blockIfLocked(packageName: String) {
    val current = settings
    val elapsed = SystemClock.elapsedRealtime()
    // Before settings load (just after boot) nothing is blocked; the first emission rechecks.
    val locked =
        current != null &&
            packageName != this.packageName &&
            AppLockPolicy.isLocked(
                packageName,
                current,
                Instant.now(),
                ZoneId.systemDefault(),
                protectedPackages,
            )
    if (!locked) {
      pendingRecheck?.cancel()
      pendingRecheck = null
      return
    }
    val remaining = launchDebounce.remainingMillis(packageName, elapsed)
    if (remaining > 0L) {
      if (pendingRecheck?.isActive != true) {
        pendingRecheck = scope.launch {
          delay(remaining)
          pendingRecheck = null
          // Re-evaluate the current foreground app and settings, not the stale event.
          recheck()
        }
      }
    } else {
      pendingRecheck?.cancel()
      pendingRecheck = null
      launchDebounce.launched(packageName, elapsed)
      startActivity(AppBlockedActivity.intent(this, packageName))
    }
  }

  internal companion object {
    private var running: WeakReference<AppLockAccessibilityService>? = null

    /** Called on the main thread by receivers in this process. */
    fun recheckIfRunning() {
      running?.get()?.recheck()
    }
  }
}
