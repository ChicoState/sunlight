package edu.sunlight.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class LaunchableApp(val packageName: String, val label: String)

internal data class BlockedAppsState(
    val settings: AppLockSettings,
    val statusText: String,
    val locked: Boolean,
    val accessEnabled: Boolean,
    /** Null while installed apps are still loading. */
    val apps: List<LaunchableApp>?,
)

internal data class BlockedAppsActions(
    val onToggleEnabled: (Boolean) -> Unit = {},
    val onChooseLockTime: () -> Unit = {},
    val onToggleApp: (String, Boolean) -> Unit = { _, _ -> },
    val onOpenAccessibilitySettings: () -> Unit = {},
    val onOpenAppInfo: () -> Unit = {},
    val onEmergencyUnlock: () -> Unit = {},
)

private val panelShape = RoundedCornerShape(16.dp)

@Suppress("FunctionNaming", "LongMethod")
@Composable
internal fun BlockedAppsPage(
    state: BlockedAppsState,
    night: Boolean,
    actions: BlockedAppsActions,
    appIcon: @Composable (String) -> Unit = {},
) {
  val accent = if (night) SunlightPalette.nightAccent else SunlightPalette.sunlightAccent
  val secondary = if (night) SunlightPalette.nightSecondary else SunlightPalette.secondaryText
  val primary = if (night) SunlightPalette.nightAccent else SunlightPalette.primaryText
  val surface = if (night) SunlightPalette.nightSurface else SunlightPalette.surface
  val editable = !state.locked
  Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        BasicText(
            "Blocked apps",
            style = TextStyle(color = accent, fontSize = 28.sp),
            modifier = Modifier.testTag("page-Blocked apps").semantics { heading() },
        )
      }
      item {
        BasicText(
            state.statusText,
            style = TextStyle(color = primary, fontSize = 16.sp, lineHeight = 24.sp),
            modifier =
                Modifier.testTag("lock-status").semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
      if (!state.accessEnabled) {
        item { AccessSetupPanel(accent, primary, secondary, surface, actions) }
      }
      item {
        Column(Modifier.fillMaxWidth().background(surface, panelShape)) {
          SettingRow(
              label = "Lock apps nightly",
              value = if (state.settings.enabled) "On" else "Off",
              color = primary,
              accent = accent,
              enabled = editable,
              modifier =
                  Modifier.testTag("lock-enabled")
                      .toggleable(
                          value = state.settings.enabled,
                          enabled = editable,
                          role = Role.Switch,
                          onValueChange = actions.onToggleEnabled,
                      ),
          )
          SettingRow(
              label = "Lock time",
              value = displayTime(state.settings.lockTime),
              color = primary,
              accent = accent,
              enabled = editable,
              modifier =
                  Modifier.testTag("lock-time")
                      .clickable(
                          enabled = editable,
                          role = Role.Button,
                          onClickLabel = "Edit lock time",
                          onClick = actions.onChooseLockTime,
                      ),
          )
        }
      }
      if (state.locked) {
        item {
          RoutineLabel(
              "Settings are frozen until your morning sunlight timer finishes. Ending the " +
                  "routine early does not unlock apps. An emergency unlock lasts until the next " +
                  "lock time and is recorded as an emergency, not a completed routine.",
              secondary,
          )
        }
        item { EmergencyUnlockAction(accent, actions.onEmergencyUnlock) }
      }
      item {
        RoutineLabel(
            "Sunlight, the phone app, Settings, and your home screen are never locked. " +
                "Debug sunlight tests do not unlock apps.",
            secondary,
        )
      }
      item {
        BasicText(
            "Apps to lock",
            style = TextStyle(color = primary, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.semantics { heading() },
        )
      }
      val apps = state.apps
      if (apps == null) {
        item { RoutineLabel("Loading installed apps…", secondary) }
      } else if (apps.isEmpty()) {
        item { RoutineLabel("No other launchable apps were found.", secondary) }
      } else {
        items(apps, key = { it.packageName }) { app ->
          AppRow(
              app = app,
              checked = app.packageName in state.settings.packages,
              enabled = editable,
              color = primary,
              accent = accent,
              onToggle = { actions.onToggleApp(app.packageName, it) },
              icon = { appIcon(app.packageName) },
          )
        }
      }
    }
  }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun AccessSetupPanel(
    accent: Color,
    primary: Color,
    secondary: Color,
    surface: Color,
    actions: BlockedAppsActions,
) {
  Column(
      Modifier.fillMaxWidth()
          .background(surface, panelShape)
          .padding(16.dp)
          .testTag("access-setup"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BasicText(
        "Turn on app locking",
        style = TextStyle(color = primary, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.semantics { heading() },
    )
    RoutineLabel(
        "Apps are not locked until you turn on the Sunlight app lock accessibility service. " +
            "It only sees which app opens, never screen content, and nothing leaves your phone.",
        secondary,
    )
    RoutineAction(
        "Open Accessibility settings",
        actions.onOpenAccessibilitySettings,
        filled = true,
        accent = accent,
    )
    RoutineLabel(
        "If Android says the setting is restricted, open App info, choose the ⋮ menu, select " +
            "Allow restricted settings, then try again.",
        secondary,
    )
    RoutineAction("Open App info", actions.onOpenAppInfo, filled = false, accent = accent)
  }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SettingRow(
    label: String,
    value: String,
    color: Color,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier,
) {
  Row(
      modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicText(
        label,
        style = TextStyle(color = color, fontSize = 16.sp),
        modifier = Modifier.weight(1f),
    )
    BasicText(
        value,
        style =
            TextStyle(
                color = if (enabled) accent else SunlightPalette.outline,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
    )
  }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun AppRow(
    app: LaunchableApp,
    checked: Boolean,
    enabled: Boolean,
    color: Color,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth()
          .heightIn(min = 56.dp)
          .testTag("app-${app.packageName}")
          .toggleable(
              value = checked,
              enabled = enabled,
              role = Role.Checkbox,
              onValueChange = onToggle,
          )
          .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
    BasicText(
        app.label,
        style = TextStyle(color = color, fontSize = 16.sp),
        modifier = Modifier.weight(1f),
    )
    CheckboxMark(checked, accent, enabled)
  }
}
