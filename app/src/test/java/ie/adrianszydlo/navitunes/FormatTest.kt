package ie.adrianszydlo.navitunes

import ie.adrianszydlo.navitunes.ui.common.formatBytes
import ie.adrianszydlo.navitunes.ui.common.formatDuration
import ie.adrianszydlo.navitunes.ui.common.formatDurationLong
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test fun durationZeroAndNegative() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-30))
    }

    @Test fun durationUnderAnHour() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("1:05", formatDuration(65))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test fun durationOverAnHour() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:01:01", formatDuration(3661))
        assertEquals("2:30:00", formatDuration(9000))
    }

    @Test fun durationLong() {
        assertEquals("<1m", formatDurationLong(0))
        assertEquals("<1m", formatDurationLong(45))
        assertEquals("3m", formatDurationLong(200))
        assertEquals("1h 1m", formatDurationLong(3661))
    }

    @Test fun bytesBelowKilobyte() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test fun bytesKilobytes() {
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("2 KB", formatBytes(2048))
    }

    @Test fun bytesMegabytesUseDotDecimal() {
        // Locale-pinned: must be a '.' separator, never ','.
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.5 MB", formatBytes(1024L * 1024 * 3 / 2))
    }

    @Test fun bytesGigabytes() {
        assertEquals("1.00 GB", formatBytes(1024L * 1024 * 1024))
    }
}
