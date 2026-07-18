package ie.adrianszydlo.navitunes

import ie.adrianszydlo.navitunes.data.update.compareVersions
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the semantic-version comparison the update detector relies on. */
class UpdateVersionTest {

    @Test fun newerPatchIsGreater() {
        assertTrue(compareVersions("0.4.1", "0.4.0") > 0)
    }

    @Test fun newerMinorIsGreater() {
        assertTrue(compareVersions("0.5.0", "0.4.9") > 0)
    }

    @Test fun doubleDigitBeatsSingleDigit() {
        // Numeric, not lexicographic: 0.10.0 must outrank 0.9.0.
        assertTrue(compareVersions("0.10.0", "0.9.0") > 0)
    }

    @Test fun equalVersionsAreEqual() {
        assertTrue(compareVersions("1.2.3", "1.2.3") == 0)
    }

    @Test fun olderIsLess() {
        assertTrue(compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test fun differingComponentCountsCompare() {
        assertTrue(compareVersions("1.2", "1.2.0") == 0)
        assertTrue(compareVersions("1.2.1", "1.2") > 0)
    }

    @Test fun releaseOutranksPrerelease() {
        // A full release is newer than its own pre-release.
        assertTrue(compareVersions("1.0.0", "1.0.0-rc1") > 0)
        assertTrue(compareVersions("1.0.0-beta", "1.0.0") < 0)
    }
}
