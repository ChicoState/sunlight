package edu.sunlight.screentime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager

/**
 * Packages that are never locked: Sunlight, every home-screen app, dialers, and Settings. Package
 * visibility for these queries is declared in the manifest `<queries>` element.
 */
internal fun resolveProtectedPackages(context: Context): Set<String> {
  val pm = context.packageManager
  fun resolvers(intent: Intent): List<String> =
      pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map {
        it.activityInfo.packageName
      }
  val resolved =
      resolvers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) +
          resolvers(Intent(Intent.ACTION_DIAL)) +
          resolvers(Intent(Settings.ACTION_SETTINGS)) +
          listOfNotNull(context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage)
  return AppSelection.protectedPackages(context.packageName, resolved.toSet())
}

/** Launchable apps the user may lock, sorted by label, with protected packages removed. */
internal fun loadLaunchableApps(context: Context): List<LaunchableApp> {
  val pm = context.packageManager
  val protected = resolveProtectedPackages(context)
  val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm.queryIntentActivities(launcher, 0)
      .asSequence()
      .map { it.activityInfo }
      .filter { AppSelection.isValidPackageName(it.packageName) }
      .filterNot {
        it.packageName in protected || it.packageName in AppSelection.BUILT_IN_PROTECTED
      }
      .distinctBy { it.packageName }
      .map { LaunchableApp(it.packageName, it.applicationInfo.loadLabel(pm).toString()) }
      .sortedBy { it.label.lowercase() }
      .toList()
}

internal fun loadAppIcon(context: Context, packageName: String, sizePx: Int): Bitmap? =
    runCatching {
      val drawable = context.packageManager.getApplicationIcon(packageName)
      Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
      }
    }
    .getOrNull()

internal fun loadAppLabel(context: Context, packageName: String): String? = runCatching {
  val pm = context.packageManager
  pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}
    .getOrNull()

internal fun isAppLockServiceEnabled(context: Context): Boolean {
  val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
  return manager
      .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      .any {
        val info = it.resolveInfo.serviceInfo
        info.packageName == context.packageName &&
            info.name == AppLockAccessibilityService::class.java.name
      }
}
