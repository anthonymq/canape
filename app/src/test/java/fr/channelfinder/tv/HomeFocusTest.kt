package fr.channelfinder.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFocusTest {
    @Test fun firstRemovalSelectsNextCard() {
        assertEquals("fav:b", homeFocusAfterRemoval(listOf("fav:a", "fav:b", "fav:c"), "fav:a", "lane:FRANCE"))
    }

    @Test fun middleRemovalSelectsCardTakingItsPlace() {
        assertEquals("cat:c", homeFocusAfterRemoval(listOf("cat:a", "cat:b", "cat:c"), "cat:b", "cat:add"))
    }

    @Test fun lastRemovalSelectsPreviousCard() {
        assertEquals("fav:b", homeFocusAfterRemoval(listOf("fav:a", "fav:b", "fav:c"), "fav:c", "lane:FRANCE"))
    }

    @Test fun soleRemovalSelectsRowRecovery() {
        assertEquals("cat:add", homeFocusAfterRemoval(listOf("cat:a"), "cat:a", "cat:add"))
        assertEquals("lane:FRANCE", homeFocusAfterRemoval(listOf("fav:a"), "fav:a", "lane:FRANCE"))
    }

    @Test fun removingLastVisibleFavoriteSelectsPromotedCard() {
        val favorites = (1..13).map { "fav:$it" }
        assertEquals("fav:13", homeFocusAfterRemoval(favorites, "fav:12", "lane:FRANCE", visibleLimit = 12))
    }
}
