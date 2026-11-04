package edu.sunlight.screentime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wakes the app at the next lock time so an app that is already open when the lock starts gets
 * blocked. Lock correctness never depends on this alarm: every foreground change is evaluated
 * against the persisted policy, so an inexact (Doze-deferred) alarm only delays that one case.
 */
internal object AppLockScheduler {
  fun schedule(context: Context, settings: AppLockSettings, now: Instant) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val intent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AppLockAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    if (!settings.enabled || settings.packages.isEmpty()) {
      alarms.cancel(intent)
      return
    }
    val next = AppLockPolicy.nextLockAt(now, settings.lockTime, ZoneId.systemDefault())
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), intent)
  }

  /** Reads persisted settings and schedules the next alarm without blocking a receiver. */
  fun rescheduleAsync(receiver: BroadcastReceiver, context: Context) {
    val pending = receiver.goAsync()
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob()).launch {
      try {
        schedule(appContext, appContext.appLockRepository.current(), Instant.now())
      } finally {
        pending.finish()
      }
    }
  }
}

/** Fires at the lock time; re-evaluates the current foreground app and schedules tomorrow. */
class AppLockAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AppLockAccessibilityService.recheckIfRunning()
    AppLockScheduler.rescheduleAsync(this, context)
  }
}

/** Alarms are cleared by reboot and depend on wall-clock time, so reschedule on these events. */
class AppLockRescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in RESCHEDULE_ACTIONS) return
    AppLockAccessibilityService.recheckIfRunning()
    AppLockScheduler.rescheduleAsync(this, context)
  }

  private companion object {
    val RESCHEDULE_ACTIONS =
        setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
  }
}
