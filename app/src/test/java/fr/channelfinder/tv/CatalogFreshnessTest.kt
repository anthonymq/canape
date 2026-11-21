package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class CatalogFreshnessTest {
    private val hour = 60 * 60 * 1000L
    private val saved = 1_800_000_000_000L

    @Test fun catalogsYoungerThanSixHoursSkipTheStartupDownload() {
        assertTrue(catalogIsFresh(saved, saved))
        assertTrue(catalogIsFresh(saved, saved + 6 * hour - 1))
        assertFalse(catalogIsFresh(saved, saved + 6 * hour))
    }

    @Test fun aCatalogDatedInTheFutureIsRefreshed() {
        assertFalse(catalogIsFresh(saved, saved - 1))
    }
}
