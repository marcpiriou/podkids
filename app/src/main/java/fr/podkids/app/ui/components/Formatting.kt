package fr.podkids.app.ui.components

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** « 4:07 » ou « 1:02:45 » pour la position de lecture. */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** « 12 min » ou « 1 h 05 » pour la durée d'un épisode. */
fun formatDuration(seconds: Long): String {
    val minutes = ((seconds + 30) / 60).coerceAtLeast(1)
    return if (minutes >= 60) {
        String.format(Locale.ROOT, "%d h %02d", minutes / 60, minutes % 60)
    } else {
        "$minutes min"
    }
}

fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
