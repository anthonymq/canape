package fr.channelfinder.tv

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class BackgroundCalculationTest {
    @Test fun movesWorkToAnotherThread() = runBlocking {
        val caller = Thread.currentThread()
        val worker = calculateInBackground { Thread.currentThread() }
        assertNotSame(caller, worker)
    }

    @Test fun keepsAConsistentSnapshotDuringCalculation() = runBlocking {
        val query = mutableStateOf("old")
        Snapshot.sendApplyNotifications()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val result = async {
            calculateInBackground {
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                query.value
            }
        }
        yield()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            query.value = "new"
            Snapshot.sendApplyNotifications()
        } finally {
            finish.countDown()
        }
        assertEquals("old", result.await())
        assertEquals("new", query.value)
    }

    @Test fun cancelledCalculationCannotPublishItsResult() = runBlocking {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var published = false
        var completedWork = false
        val result = async {
            calculateInBackground { checkActive ->
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                checkActive()
                completedWork = true
                "obsolete"
            }
            published = true
        }
        yield()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            result.cancel()
        } finally {
            finish.countDown()
        }
        result.join()
        assertTrue(result.isCancelled)
        assertFalse(published)
        assertFalse(completedWork)
    }
}
