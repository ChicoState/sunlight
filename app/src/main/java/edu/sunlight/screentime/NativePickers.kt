package edu.sunlight.screentime

import android.app.AlertDialog
import android.widget.NumberPicker
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import java.time.LocalTime

private const val DEFAULT_RELEASE_HOUR = 7

/** Native spinner dialog for the wake-up time; instrumented tests drive the TimePicker directly. */
internal fun ComponentActivity.showWakeUpTimePicker(
    current: LocalTime?,
    onSelected: (LocalTime) -> Unit,
) {
  val initial = current ?: LocalTime.of(DEFAULT_RELEASE_HOUR, 0)
  val pickerView = layoutInflater.inflate(R.layout.release_time_picker, null)
  val picker = pickerView.findViewById<TimePicker>(R.id.release_time_picker)
  picker.setIs24HourView(false)
  picker.hour = initial.hour
  picker.minute = initial.minute
  AlertDialog.Builder(this)
      .setTitle("Wake-up time")
      .setView(pickerView)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton("Set time") { _, _ ->
        // Commit any keyboard edit before reading the native spinner's value.
        picker.clearFocus()
        onSelected(LocalTime.of(picker.hour, picker.minute))
      }
      .show()
}

/** Native NumberPicker dialog for the sunlight duration, bounded to 1–120 minutes. */
internal fun ComponentActivity.showDurationPicker(current: Int, onSelected: (Int) -> Unit) {
  val picker =
      NumberPicker(this).apply {
        minValue = 1
        maxValue = MAX_SUNLIGHT_MINUTES
        value = current
        wrapSelectorWheel = false
        contentDescription = "Sunlight duration in minutes"
      }
  AlertDialog.Builder(this)
      .setTitle("Sunlight duration")
      .setMessage("Choose 1–120 minutes.")
      .setView(picker)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton("Save") { _, _ ->
        picker.clearFocus()
        onSelected(picker.value)
      }
      .show()
}
