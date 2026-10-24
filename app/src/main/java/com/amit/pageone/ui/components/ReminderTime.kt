package com.amit.pageone.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.text.DateFormat
import java.util.Calendar

/** "7:30 pm", in the phone's own 12/24-hour style. */
fun timeLabel(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.time)
}

/** A clock for choosing exactly when the writing reminder should go off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (hour: Int, minute: Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me at") },
        text = { Column(Modifier) { TimePicker(state) } },
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
