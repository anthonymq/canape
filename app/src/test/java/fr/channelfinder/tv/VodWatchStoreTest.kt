package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class VodWatchStoreTest {
    private val movie = LibraryItem("film", "Film", extension = "mkv")
    private val series = LibraryItem("series", "Série", artwork = "cover")

    @Test fun storedProgressRoundTripsWithEpisodeParentAndFormat() {
        val episode = LibraryItem("episode", "Épisode 2", extension = "mkv", season = 1, episode = 2)
        val entry = VodWatch(episode, LibraryKind.SERIES, series, 125_000, 1_800_000, 10, false)
        val restored = parseWatchHistory(serializeWatchHistory(listOf(entry))).single()
        assertEquals(entry, restored)
        assertEquals(125_000L, restored.resumeMs)
        assertEquals("mkv", restored.item.extension)
    }

    @Test fun completionRequiresKnownDurationOrActualEnd() {
        assertFalse(watchCompleted(60_000, 0, false))
        assertFalse(watchCompleted(940, 1000, false))
        assertTrue(watchCompleted(950, 1000, false))
        assertTrue(watchCompleted(0, 0, true))
        assertEquals(0L, VodWatch(movie, LibraryKind.MOVIES, null, 1000, 1000, 1, true).resumeMs)
    }

    @Test fun continueWatchingKeepsLatestEpisodeAndSeparatesKinds() {
        val first = VodWatch(LibraryItem("1", "E1"), LibraryKind.SERIES, series, 50_000, 100_000, 1, false)
        val second = first.copy(item = LibraryItem("2", "E2"), updatedAt = 2)
        val film = VodWatch(movie, LibraryKind.MOVIES, null, 25_000, 100_000, 3, false)
        assertEquals(listOf(second), continueWatching(listOf(first, film, second), LibraryKind.SERIES))
        assertEquals(listOf(film), continueWatching(listOf(first, film), LibraryKind.MOVIES))
        assertTrue(continueWatching(listOf(first, second.copy(completed = true)), LibraryKind.SERIES).isEmpty())
        assertTrue(continueWatching(listOf(film.copy(positionMs = 1000)), LibraryKind.MOVIES).isEmpty())
    }
}
