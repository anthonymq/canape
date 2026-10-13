package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class ConfirmPressGuardTest {
    @Test fun removedCardCannotActivateReplacementOnRelease() {
        val guard = ConfirmPressGuard()
        assertFalse(guard.onEvent(23, 100, true))
        guard.consumeCurrentPress()
        assertTrue(guard.onEvent(23, 100, true))
        assertTrue(guard.onEvent(23, 100, false))
        assertFalse(guard.onEvent(23, 200, true))
        assertFalse(guard.onEvent(23, 200, false))
    }

    @Test fun missingReleaseDoesNotConsumeTheNextPress() {
        val guard = ConfirmPressGuard()
        guard.onEvent(23, 100, true)
        guard.consumeCurrentPress()
        assertFalse(guard.onEvent(23, 200, true))
        assertFalse(guard.onEvent(23, 200, false))
    }

    @Test fun accessibilityRemovalDoesNotConsumeAnUnrelatedPress() {
        val guard = ConfirmPressGuard()
        guard.consumeCurrentPress()
        assertFalse(guard.onEvent(66, 200, true))
        assertFalse(guard.onEvent(66, 200, false))
    }
}
