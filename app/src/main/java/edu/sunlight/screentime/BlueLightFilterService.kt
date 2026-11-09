package edu.sunlight.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** Shows a non-interactive amber overlay while a focus session is backgrounded. */
class BlueLightFilterService : Service() {
  private val windowManager by lazy { getSystemService(WindowManager::class.java) }
  private var overlay: View? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return START_NOT_STICKY
    }
    showForegroundNotification()
    showOverlay()
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopSelf()
  }

  override fun onDestroy() {
    overlay?.let { windowManager.removeView(it) }
    overlay = null
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  private fun showForegroundNotification() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            "Blue-light filter",
            NotificationManager.IMPORTANCE_LOW,
        )
    )
    val stopIntent = Intent(this, BlueLightFilterService::class.java).setAction(ACTION_STOP)
    val stopPendingIntent =
        PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    val notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Blue-light filter is on")
            .setContentText(
                "An amber blue-light-reducing filter is active during your morning routine."
            )
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Turn off", stopPendingIntent).build())
            .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun showOverlay() {
    if (overlay != null) return
    val filter =
        View(this).apply {
          setBackgroundColor(FILTER_COLOR)
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    val layoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    try {
      windowManager.addView(filter, layoutParams)
      overlay = filter
    } catch (_: SecurityException) {
      stopSelf()
    }
  }

  companion object {
    const val ACTION_STOP = "edu.sunlight.screentime.action.STOP_BLUE_LIGHT_FILTER"
    const val CHANNEL_ID = "blue_light_filter"
    const val NOTIFICATION_ID = 1
    val FILTER_COLOR: Int = Color.argb(180, 255, 91, 0)

    fun start(context: Context) {
      ContextCompat.startForegroundService(
          context,
          Intent(context, BlueLightFilterService::class.java),
      )
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, BlueLightFilterService::class.java))
    }
  }
}
