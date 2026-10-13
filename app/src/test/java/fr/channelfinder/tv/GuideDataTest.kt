package fr.channelfinder.tv

import org.junit.Assert.*
import org.junit.Test

class GuideDataTest {
    @Test fun filteringKeepsTheRequestedChannelWhenItsScheduleIsMissing() {
        fun channel(id: String) = Channel(id, "Channel $id", "category", "Category", "", "", "France")
        val channels = listOf(channel("requested"), channel("missing"), channel("scheduled"), channel("pending"))
        val availability = mapOf("requested" to false, "missing" to false, "scheduled" to true)
        assertEquals(listOf("requested", "scheduled", "pending"),
            guideChannelsForDisplay(channels, true, availability, "requested").map(Channel::id))
        assertEquals(channels, guideChannelsForDisplay(channels, false, availability, "requested"))
        assertTrue(guideChannelsForDisplay(emptyList(), true, availability, "requested").isEmpty())
    }

    @Test fun decodesUtf8AndPreservesPlainText() {
        assertEquals("Émission du soir", decodeGuideText("w4ltaXNzaW9uIGR1IHNvaXI="))
        assertEquals("Journal télévisé", decodeGuideText("Journal télévisé"))
        assertEquals("News", decodeGuideText("News"))
        assertEquals("A & B", decodeGuideText("<p>A &amp; B</p>"))
        assertEquals("", decodeGuideText("null"))
    }

    @Test fun ordersDeduplicatesAndSkipsBrokenIntervals() {
        val rows = parseGuide("""{"epg_listings":[
            {"title":"U29pcg==","description":"RGV0YWlscw==","start_timestamp":"1800003600","stop_timestamp":1800007200},
            {"title":"Journal","start_timestamp":1800000000,"stop_timestamp":1800003600},
            {"title":"Journal","start_timestamp":1800000000,"stop_timestamp":1800003600},
            {"title":"Broken","start_timestamp":1800000000,"stop_timestamp":1799990000},
            {"title":"No time","start":"2027-01-01 12:00:00"}
        ]}""")
        assertEquals(listOf("Journal", "Soir"), rows.map(TvProgramme::title))
        assertEquals(1800000000000L, rows.first().start)
        assertEquals("Details", rows.last().description)
        assertTrue(rows.first().isLive(1800000000000L))
        assertFalse(rows.first().isLive(1800003600000L))
    }

    @Test fun acceptsMillisAndEmptySchedule() {
        assertTrue(parseGuide("[]").isEmpty())
        assertTrue(parseGuide("""{"epg_listings":[]}""").isEmpty())
        assertEquals(1800000000000L, parseGuide("""{"epg_listings":[{"title":"Programme","start_timestamp":1800000000000,"stop_timestamp":1800003600000}]}""").single().start)
    }

    @Test(expected = CatalogException::class) fun rejectsAuthenticationFailure() {
        parseGuide("""{"user_info":{"auth":0}}""")
    }

    @Test(expected = CatalogException::class) fun rejectsUnexpectedObject() { parseGuide("{}") }
}
