package fr.channelfinder.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserFiltersTest {
    @Test fun countryPreferencesAreIndependentAcrossLanesAndCategories() {
        val keys = listOf(
            Page.Browse(BrowseLane.FRANCE), Page.Browse(BrowseLane.PORTUGUESE),
            Page.Browse(BrowseLane.ALL), Page.Browse(BrowseLane.ALL, categoryId = "FRANCE"),
            Page.Browse(BrowseLane.ALL, categoryId = "PORTUGUESE")
        ).map(::browseCountryPreferenceKey)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(browseCountryPreferenceKey(Page.Browse(BrowseLane.FRANCE)),
            browseCountryPreferenceKey(Page.Browse(BrowseLane.FRANCE, query = "TF1", country = "FR")))
        assertEquals(browseCountryPreferenceKey(Page.Browse(BrowseLane.FRANCE, categoryId = "one")),
            browseCountryPreferenceKey(Page.Browse(BrowseLane.ALL, categoryId = "one")))
    }

    private fun channel(id: Int, prefix: String) = Channel(id.toString(), "$prefix: Chaîne $id", "test", "Test", "", "", "")

    @Test fun minorityCountriesRemainReachable() {
        val channels = (1..100).map { channel(it, "FR") } +
            listOf("PT", "ES", "IT", "DE", "GB", "US", "BE").mapIndexed { index, code -> channel(101 + index, code) }
        assertEquals(listOf("FR", "BE", "DE", "ES", "GB", "IT", "PT", "US"), browseCountryOptions(channels))
    }

    @Test fun aSingleCountryStillHasASelectableFilter() {
        assertEquals(listOf("PT"), browseCountryOptions(listOf(channel(1, "PT"))))
    }

    @Test fun unknownPrefixesDoNotInventCountries() {
        assertEquals(emptyList<String>(), browseCountryOptions(listOf(channel(1, "EN"))))
    }
}
