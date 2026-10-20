package fr.channelfinder.tv

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class PlaybackProblemTest {
    @Test fun networkFailureDoesNotBlacklistTheChannel() {
        val problem = classifyPlaybackProblem(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        assertEquals(PlaybackFailureKind.NETWORK, problem.kind)
        assertTrue(problem.retryAutomatically)
        assertFalse(problem.marksChannelUnavailable)
    }

    @Test fun accountConcurrencyIsNotADeadChannel() {
        listOf(429, 458, 509).forEach { status ->
            val problem = classifyPlaybackProblem(2004, status)
            assertEquals(PlaybackFailureKind.BUSY, problem.kind)
            assertFalse(problem.retryAutomatically)
            assertFalse(problem.marksChannelUnavailable)
        }
    }

    @Test fun providerRefusalAndServerFailureMayRecover() {
        listOf(407, 503).forEach { status ->
            val problem = classifyPlaybackProblem(2004, status)
            assertTrue(problem.retryAutomatically)
            assertTrue(problem.marksChannelUnavailable)
        }
    }

    @Test fun accessMissingAndUnsupportedDoNotRetryAutomatically() {
        listOf(classifyPlaybackProblem(2004, 403), classifyPlaybackProblem(2004, 404),
            classifyPlaybackProblem(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)).forEach {
            assertFalse(it.retryAutomatically)
            assertTrue(it.marksChannelUnavailable)
        }
    }
}
