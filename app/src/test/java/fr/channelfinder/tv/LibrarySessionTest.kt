package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class LibrarySessionTest {
    @Test fun categoryChangesClearStaleSelectionAndSearchWithoutDiscardingCatalog() {
        val session = LibrarySession(null)
        val title = LibraryItem("title", "Title", categoryId = "first")
        val catalog = LibraryCatalog(emptyList(), listOf(title))
        session.catalog = catalog
        session.category = "first"
        session.query = "old search"
        session.focusedId = title.id
        session.resumeFocusId = "resume"
        session.highlighted = title
        session.grid.requestScrollToItem(120, 24)
        assertEquals(120, session.grid.firstVisibleItemIndex)
        val previousGrid = session.grid

        session.selectCategory("second")

        assertEquals("second", session.category)
        assertEquals("", session.query)
        assertNull(session.focusedId)
        assertNull(session.resumeFocusId)
        assertNull(session.highlighted)
        assertNotSame(previousGrid, session.grid)
        assertEquals(0, session.grid.firstVisibleItemIndex)
        assertEquals(0, session.grid.firstVisibleItemScrollOffset)
        assertSame(catalog, session.catalog)
    }

    @Test fun selectingTheCurrentCategoryPreservesBackNavigationState() {
        val session = LibrarySession(null)
        session.category = "first"
        session.focusedId = "title"
        val previousGrid = session.grid
        session.selectCategory("first")
        assertEquals("title", session.focusedId)
        assertSame(previousGrid, session.grid)
    }

    @Test fun clearingSearchInTheCurrentCategoryStartsAtTheTop() {
        val session = LibrarySession(null)
        session.category = "first"
        session.query = "old search"
        session.focusedId = "title"
        val previousGrid = session.grid
        session.selectCategory("first")
        assertEquals("", session.query)
        assertNull(session.focusedId)
        assertNotSame(previousGrid, session.grid)
    }

    @Test fun continueWatchingSurvivesCategoryChangesForMoviesAndSeries() {
        LibraryKind.entries.forEach { kind ->
            val session = LibrarySession(null)
            val title = LibraryItem("title", "Title", categoryId = "first")
            val parent = if (kind == LibraryKind.SERIES) LibraryItem("series", "Series") else null
            val watched = VodWatch(title, kind, parent, 25_000, 100_000, 1, false)
            val resume = continueWatching(listOf(watched), kind)
            session.selectCategory("second")
            assertTrue("Unfinished $kind titles must remain accessible in another category", session.showsContinueWatching(resume))
            session.selectCategory(null)
            assertTrue(session.showsContinueWatching(resume))
            session.query = "search"
            assertFalse(session.showsContinueWatching(resume))
        }
    }

    @Test fun categorySwitchesDoNotChangeTheOtherLibrarySession() {
        val movies = LibrarySession(null)
        val series = LibrarySession(null)
        series.category = "series-category"
        series.focusedId = "series-title"
        val seriesGrid = series.grid
        movies.selectCategory("movie-category")
        assertEquals("series-category", series.category)
        assertEquals("series-title", series.focusedId)
        assertSame(seriesGrid, series.grid)
    }
}
