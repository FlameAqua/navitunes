package ie.adrianszydlo.navitunes.ui.common

import java.util.Locale

// Use a fixed locale so decimal separators are always '.' regardless of the
// device's region — otherwise a German phone would render "1,5 MB".
private val FMT_LOCALE = Locale.US

fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(FMT_LOCALE, h, m, s)
    else "%d:%02d".format(FMT_LOCALE, m, s)
}

fun formatDurationLong(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(FMT_LOCALE, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(FMT_LOCALE, mb)
    return "%.2f GB".format(FMT_LOCALE, mb / 1024.0)
}
