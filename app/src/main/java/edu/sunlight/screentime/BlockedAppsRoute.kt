package edu.sunlight.screentime

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TimePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REFRESH_MILLIS = 1_000L
private const val ICON_SIZE_PX = 96

/** Stateful wrapper that connects [BlockedAppsPage] to DataStore, PackageManager and Settings. */
@Suppress("FunctionNaming")
@Composable
internal fun BlockedAppsRoute(night: Boolean, io: CoroutineDispatcher = Dispatchers.IO) {
  val context = LocalContext.current
  val repository = remember { context.appLockRepository }
  val settings by repository.settings.collectAsState(initial = null)
  var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
  var now by remember { mutableStateOf(Instant.now()) }
  var accessEnabled by remember { mutableStateOf(isAppLockServiceEnabled(context)) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) { apps = withContext(io) { loadLaunchableApps(context) } }
  LaunchedEffect(Unit) {
    // Settings can change outside the app (Accessibility) and lock state changes with time.
    while (isActive) {
      now = Instant.now()
      accessEnabled = isAppLockServiceEnabled(context)
      delay(REFRESH_MILLIS)
    }
  }
  val current = settings ?: return
  val zone = ZoneId.systemDefault()
  val status = AppLockPolicy.status(current, now, zone)

  BlockedAppsPage(
      state =
          BlockedAppsState(
              settings = current,
              statusText = appLockStatusText(status, zone),
              locked = status is AppLockStatus.Locked,
              accessEnabled = accessEnabled,
              apps = apps,
          ),
      night = night,
      actions = blockedAppsActions(context, repository, scope, current, apps.orEmpty()),
      appIcon = { packageName -> AppIcon(packageName, io) },
  )
}

private fun blockedAppsActions(
    context: Context,
    repository: AppLockRepository,
    scope: CoroutineScope,
    current: AppLockSettings,
    apps: List<LaunchableApp>,
): BlockedAppsActions {
  fun save(edit: suspend (Instant) -> Unit) {
    scope.launch {
      edit(Instant.now())
      AppLockScheduler.schedule(context, repository.current(), Instant.now())
    }
  }
  return BlockedAppsActions(
      onToggleEnabled = { enabled -> save { repository.setEnabled(enabled, it) } },
      onChooseLockTime = {
        showLockTimePicker(context, current.lockTime) { time ->
          save { repository.setLockTime(time, it) }
        }
      },
      onToggleApp = { packageName, checked ->
        val launchable = apps.map { it.packageName }.toSet()
        save { repository.setPackageSelected(packageName, checked, launchable, it) }
      },
      onOpenAccessibilitySettings = {
        context.openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
      },
      onOpenAppInfo = {
        context.openSettings(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
      },
      onEmergencyUnlock = { scope.launch { repository.recordEmergencyUnlock(Instant.now()) } },
  )
}

@Suppress("FunctionNaming")
@Composable
private fun AppIcon(packageName: String, io: CoroutineDispatcher) {
  val context = LocalContext.current
  val bitmap by
      produceState<android.graphics.Bitmap?>(null, packageName) {
        value = withContext(io) { loadAppIcon(context, packageName, ICON_SIZE_PX) }
      }
  bitmap?.let {
    // The adjacent label names the app, so the icon is decorative.
    Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
  }
}

private fun Context.openSettings(intent: Intent) {
  try {
    startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    startActivity(Intent(Settings.ACTION_SETTINGS))
  }
}

/** Reuses the wake-up time's native TimePicker dialog pattern. */
internal fun showLockTimePicker(context: Context, current: LocalTime, onSet: (LocalTime) -> Unit) {
  val pickerView = LayoutInflater.from(context).inflate(R.layout.release_time_picker, null)
  val picker = pickerView.findViewById<TimePicker>(R.id.release_time_picker)
  picker.setIs24HourView(false)
  picker.hour = current.hour
  picker.minute = current.minute
  picker.contentDescription = "Nightly lock time"
  AlertDialog.Builder(context)
      .setTitle("Lock time")
      .setMessage("Selected apps lock at this time each night.")
      .setView(pickerView)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton("Set time") { _, _ ->
        picker.clearFocus()
        onSet(LocalTime.of(picker.hour, picker.minute))
      }
      .show()
}
