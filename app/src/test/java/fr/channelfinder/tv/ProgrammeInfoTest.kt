package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class ProgrammeInfoTest {
    private val first = TvProgramme("Premier", "", 100, 200)
    private val second = TvProgramme("Suivant", "", 200, 300)

    @Test fun currentAndNextAreOrderedAndProgressIsMeasured() {
        val window = programmeWindow(listOf(second, first), 150)
        assertEquals(first, window.current)
        assertEquals(second, window.next)
        assertEquals(0.5f, window.progress)
    }

    @Test fun endBelongsToFollowingProgramme() {
        val window = programmeWindow(listOf(first, second), 200)
        assertEquals(second, window.current)
        assertNull(window.next)
        assertEquals(0f, window.progress)
    }

    @Test fun gapsAndExpiredSchedulesNeverInventACurrentProgramme() {
        val window = programmeWindow(listOf(first, second.copy(start = 250)), 225)
        assertNull(window.current)
        assertEquals(250L, window.next?.start)
        assertNull(programmeWindow(listOf(first), 400).next)
        assertNull(programmeWindow(emptyList(), 150).current)
    }

    @Test fun overlappingListingsUseLatestStartAndSkipOverlappingNext() {
        val overlap = TvProgramme("Correction", "", 140, 240)
        val future = TvProgramme("Après", "", 240, 320)
        val window = programmeWindow(listOf(second, first, future, overlap), 150)
        assertEquals(overlap, window.current)
        assertEquals(future, window.next)
    }
}
