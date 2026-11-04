package edu.sunlight.screentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Full-screen block shown over a locked app. Not exported: only the in-process accessibility
 * service starts it. The package extra is still validated, and the label comes from PackageManager.
 */
class AppBlockedActivity : ComponentActivity() {
  private var appLabel by mutableStateOf(DEFAULT_LABEL)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    readIntent(intent)
    onBackPressedDispatcher.addCallback(this) { goHome() }
    val repository = appLockRepository
    setContent {
      val settings by repository.settings.collectAsState(initial = null)
      val current = settings ?: return@setContent
      val zone = ZoneId.systemDefault()
      val status = AppLockPolicy.status(current, Instant.now(), zone)
      LaunchedEffect(status) { if (status !is AppLockStatus.Locked) finish() }
      AppBlockedScreen(
          appLabel = appLabel,
          statusText = appLockStatusText(status, zone),
          onGoHome = ::goHome,
          onOpenSunlight = ::openSunlight,
          onEmergencyUnlock = {
            lifecycleScope.launch { if (repository.recordEmergencyUnlock(Instant.now())) finish() }
          },
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    readIntent(intent)
  }

  private fun readIntent(intent: Intent) {
    val packageName = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf(AppSelection::isValidPackageName)
    appLabel = packageName?.let { loadAppLabel(this, it) } ?: DEFAULT_LABEL
  }

  private fun goHome() {
    startActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    finish()
  }

  private fun openSunlight() {
    startActivity(
        Intent(this, SunlightActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    finish()
  }

  internal companion object {
    private const val EXTRA_PACKAGE = "edu.sunlight.screentime.extra.BLOCKED_PACKAGE"
    private const val DEFAULT_LABEL = "This app"

    fun intent(context: Context, packageName: String): Intent =
        Intent(context, AppBlockedActivity::class.java)
            .putExtra(EXTRA_PACKAGE, packageName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
  }
}

@Suppress("FunctionNaming")
@Composable
internal fun AppBlockedScreen(
    appLabel: String,
    statusText: String,
    onGoHome: () -> Unit,
    onOpenSunlight: () -> Unit,
    onEmergencyUnlock: () -> Unit,
) {
  Column(
      Modifier.fillMaxSize().background(SunlightPalette.background).safeDrawingPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
        Modifier.widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      BasicText(
          "$appLabel is locked",
          style =
              TextStyle(
                  color = SunlightPalette.sunlightAccent,
                  fontSize = 28.sp,
                  fontWeight = FontWeight.Medium,
              ),
          modifier = Modifier.semantics { heading() },
      )
      RoutineLabel(statusText, SunlightPalette.primaryText)
      RoutineLabel(
          "Finish your morning sunlight timer in Sunlight to unlock your apps.",
          SunlightPalette.secondaryText,
      )
      RoutineAction("Go to home screen", onGoHome)
      RoutineAction("Open Sunlight", onOpenSunlight, filled = false)
      EmergencyUnlockAction(SunlightPalette.secondaryText, onEmergencyUnlock)
      RoutineLabel(
          "An emergency unlock lasts until the next lock time and is recorded as an emergency, " +
              "not as a completed routine.",
          SunlightPalette.secondaryText,
      )
    }
  }
}
