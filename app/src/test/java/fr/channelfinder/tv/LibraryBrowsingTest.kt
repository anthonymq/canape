package fr.channelfinder.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryBrowsingTest {
    private val items = listOf(
        LibraryItem("z", "Zèbre", "a", year = "2020"),
        LibraryItem("u", "1984", "a"),
        LibraryItem("e", "Éclair (2024) [4K]", "a"),
        LibraryItem("a", "Alpha", "b", year = "2024"),
        LibraryItem("bad", "Unknown", "a", year = "not a year")
    )

    @Test fun providerOrderAndEqualYearOrderRemainStableAndUnknownYearsAreLast() {
        assertEquals(items, libraryRows(items, "", null, LibrarySort.PROVIDER) {})
        assertEquals(listOf("e", "a", "z", "u", "bad"), libraryRows(items, "", null, LibrarySort.YEAR) {}.map { it.id })
        assertEquals(listOf("u", "a", "e", "bad", "z"), libraryRows(items, "", null, LibrarySort.TITLE) {}.map { it.id })
    }

    @Test fun rawSuffixesRemainSearchableAndCategoryStillAppliesToSorting() {
        assertEquals(listOf("e"), libraryRows(items, "eclair 4k 2024", "a", LibrarySort.TITLE) {}.map { it.id })
        assertTrue(libraryRows(items, "alpha", "a", LibrarySort.YEAR) {}.isEmpty())
    }

    @Test fun sortingRunsOnWorkerAndChecksCancellationDuringComparisons() = runBlocking {
        val caller = Thread.currentThread()
        var worker: Thread? = null
        val rows = calculateInBackground { active ->
            libraryRows(items, "", null, LibrarySort.YEAR) { active(); worker = Thread.currentThread() }
        }
        assertNotSame(caller, worker)
        assertEquals("e", rows.first().id)
        var checks = 0
        try {
            libraryRows(items, "", null, LibrarySort.TITLE) {
                if (++checks > items.size) throw CancellationException("cancelled during sort")
            }
            fail("A cancelled sort must not publish rows")
        } catch (_: CancellationException) {
            assertEquals(items.size + 1, checks)
        }
    }

    @Test fun suffixPresentationPreservesNumericAndAmbiguousTitles() {
        assertEquals(LibraryPresentation("Éclair", 2024, "4K"), items[2].presentation)
        listOf("1984", "2001", "2001: A Space Odyssey", "Class of 1984", "Movie 2024", "HD", "Movie (Director Cut)").forEach {
            assertEquals(LibraryPresentation(it, null, null), libraryPresentation(it, ""))
        }
        assertEquals(LibraryPresentation("Movie", 2020, "FHD"), libraryPresentation("Movie [FHD] (2020)", "2020"))
        assertEquals(LibraryPresentation("Movie (2020)", 2021, "HD"), libraryPresentation("Movie (2020) - HD", "2021"))
        assertEquals(LibraryPresentation("Movie", null, null), libraryPresentation("Movie", "0"))
        assertEquals(LibraryPresentation("Profession profiler", 2004, null), libraryPresentation("Profession profiler - 2004", ""))
        assertEquals(LibraryPresentation("Le Mac", 2010, null), libraryPresentation("Le Mac - 2010", ""))
        assertEquals(LibraryPresentation("Cujo", 1983, "4K"), libraryPresentation("Cujo - 1983 4K", ""))
        assertEquals(LibraryPresentation("Cujo - 1983 4K", 1984, null), libraryPresentation("Cujo - 1983 4K", "1984"))
        assertEquals(LibraryPresentation("2001", 1968, "HD"), libraryPresentation("2001 - 1968 HD", ""))
    }

    @Test fun sortChangesResetOnlyTheCurrentSessionPositionAndKeepFilters() {
        val movies = LibrarySession(null)
        val series = LibrarySession(null)
        movies.category = "a"
        movies.query = "eclair"
        movies.focusedId = "z"
        movies.resumeFocusId = "resume"
        movies.highlighted = items[0]
        movies.grid.requestScrollToItem(100, 10)
        val oldGrid = movies.grid
        movies.selectSort(LibrarySort.YEAR)
        assertEquals(LibrarySort.YEAR, movies.sort)
        assertEquals("a", movies.category)
        assertEquals("eclair", movies.query)
        assertNull(movies.focusedId)
        assertNull(movies.resumeFocusId)
        assertNull(movies.highlighted)
        assertNotSame(oldGrid, movies.grid)
        assertEquals(0, movies.grid.firstVisibleItemIndex)
        assertEquals(LibrarySort.PROVIDER, series.sort)
        val currentGrid = movies.grid
        movies.focusedId = "e"
        movies.selectSort(LibrarySort.YEAR)
        assertSame(currentGrid, movies.grid)
        assertEquals("e", movies.focusedId)
        movies.selectCategory("b")
        assertEquals(LibrarySort.YEAR, movies.sort)
    }

    @Test fun focusScrollingRevealsTheWholeCardAndDoesNotMoveAnAlreadyVisibleCard() {
        assertEquals(88f, libraryScrollDistance(180f, 300f, 400f, 8f), 0f)
        assertEquals(-28f, libraryScrollDistance(-20f, 300f, 400f, 8f), 0f)
        assertEquals(0f, libraryScrollDistance(8f, 300f, 400f, 8f), 0f)
        assertEquals(0f, libraryScrollDistance(92f, 300f, 400f, 8f), 0f)
        assertEquals(0f, libraryScrollDistance(8f, 500f, 400f, 8f), 0f)
    }
}
