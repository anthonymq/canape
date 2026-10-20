package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class SavedTitlesTest {
    @Test fun titlesRoundTripWithProviderArtworkAndSeparateMovieSeriesIds() {
        val film = LibraryItem("42", "Le voyage", artwork = "cover", year = "2024", rating = "7.4", backdrop = "scene")
        val series = LibraryItem("42", "La traversée")
        val titles = listOf(SavedTitle(film, LibraryKind.MOVIES), SavedTitle(series, LibraryKind.SERIES))
        assertEquals(titles, parseSavedTitles(serializeSavedTitles(titles)))
    }

    @Test fun malformedEntriesAreSkippedAndDuplicateTitlesAreCollapsed() {
        val text = """[{"kind":"UNKNOWN","item":{"id":"1","title":"X"}},
            {"kind":"MOVIES","item":{"id":"2","title":""}},
            {"kind":"SERIES","item":{"id":"3","title":"Série"}},
            {"kind":"SERIES","item":{"id":"3","title":"Série"}}]"""
        assertEquals(listOf(SavedTitle(LibraryItem("3", "Série"), LibraryKind.SERIES)), parseSavedTitles(text))
    }

    @Test fun sidebarStartsAtHomeAndKeepsSearchInTheContentToolbar() {
        assertEquals(listOf(NavDest.HOME, NavDest.FRANCE, NavDest.PORTUGUESE,
            NavDest.SPORTS, NavDest.EVENTS, NavDest.FAVORITES, NavDest.MOVIES, NavDest.SERIES,
            NavDest.SAVED, NavDest.GUIDE, NavDest.CATEGORIES, NavDest.SETTINGS),
            navigationItems.map { it.first })
        assertEquals(NavDest.entries.toSet() - setOf(NavDest.DIRECT, NavDest.SEARCH), navigationItems.map { it.first }.toSet())
        assertTrue(navigationItems.all { it.second.isNotBlank() })
    }
}
