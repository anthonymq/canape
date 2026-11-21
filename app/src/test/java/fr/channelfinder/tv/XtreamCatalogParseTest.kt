package fr.channelfinder.tv

import java.io.IOException
import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test

class XtreamCatalogParseTest {
    private fun streams(json: String, names: Map<String, String> = mapOf("1" to "FR | GENERALISTES")) =
        parseLiveStreams(StringReader(json), names)

    private fun failure(json: String): String =
        assertThrows(CatalogException::class.java) { streams(json) }.message.orEmpty()

    @Test fun channelsKeepProviderFieldsAndSkipRowsWithoutAStream() {
        val rows = streams("""[
            {"num":1,"stream_id":101,"name":" FR: TF1 HD ","category_id":"1","stream_icon":" icon ","epg_channel_id":null,"tv_archive":0,"extra":{"a":[1,2]}},
            {"stream_id":"102","name":"FR: France 2","category_id":7,"epg_channel_id":"null"},
            {"stream_id":"103","name":null},
            {"stream_id":"104","name":"   "},
            {"stream_id":"105","name":"##### FRANCE #####"},
            {"name":"Sans identifiant"},
            "not an object", 12, null
        ]""")
        assertEquals(listOf("101", "102"), rows.map { it.id })
        assertEquals("FR: TF1 HD", rows[0].name)
        assertEquals("icon", rows[0].logoUrl)
        assertEquals("", rows[0].epgId)
        assertEquals("FR | GENERALISTES", rows[0].category)
        assertEquals("7", rows[1].categoryId)
        assertEquals("Autres", rows[1].category)
        assertEquals("", rows[1].epgId)
    }

    @Test fun categoriesNeedAnIdAndFallBackToAutres() {
        val rows = parseLiveCategories(StringReader(
            """[{"category_id":"1","category_name":" FR | SPORTS "},{"category_id":2,"category_name":null},{"category_id":"","category_name":"Vide"}]"""
        ))
        assertEquals(listOf(ChannelCategory("1", "FR | SPORTS"), ChannelCategory("2", "Autres")), rows)
    }

    @Test fun accountAnswersInsteadOfListsAreExplained() {
        assertEquals(ACCOUNT_REFUSED, failure("""{"user_info":{"auth":0,"status":"Disabled"}}"""))
        assertEquals(ACCOUNT_REFUSED, failure("""{"server_info":{},"user_info":{"auth":"0"}}"""))
        assertEquals(ACCOUNT_REFUSED, failure("  "))
        assertEquals(NOT_A_LIST, failure("""{"user_info":{"auth":1}}"""))
    }

    @Test fun malformedRepliesAreUnreadableAndTruncatedOnesAreConnectionFailures() {
        assertEquals(UNREADABLE, failure("""[{"stream_id" 1}]"""))
        assertEquals(UNREADABLE, failure("\"<html>\""))
        assertEquals(UNREADABLE, failure("""[{"stream_id":"1"]"""))
        val truncated = assertThrows(IOException::class.java) { streams("""[{"stream_id":"1","name":"FR: TF1"""") }
        assertFalse(truncated is com.google.gson.stream.MalformedJsonException)
    }
}
