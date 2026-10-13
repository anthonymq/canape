package fr.channelfinder.tv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VodRepositoryTest {
    @Test fun moviesAndSeriesUseTheirOwnIdentifiers() {
        val json = JSONArray("""[{"stream_id":12,"series_id":34,"name":"Titre","container_extension":"mkv"},{"name":"Sans identifiant"}]""")
        assertEquals("12", parseLibraryItems(json, LibraryKind.MOVIES).single().id)
        assertEquals("34", parseLibraryItems(json, LibraryKind.SERIES).single().id)
        assertEquals("mkv", parseLibraryItems(json, LibraryKind.MOVIES).single().extension)
    }

    @Test fun episodesAreSortedNumericallyAndKeepTheirFormats() {
        val json = JSONObject("""{"episodes":{"10":[{"id":3,"episode_num":2,"container_extension":"mkv"}],"2":[{"id":2,"episode_num":10},{"id":1,"episode_num":2},{"title":"Invalid"}]}}""")
        val rows = parseEpisodes(json)
        assertEquals(listOf("1", "2", "3"), rows.map { it.id })
        assertEquals(listOf(2, 2, 10), rows.map { it.season })
        assertEquals("mkv", rows.last().extension)
    }

    @Test fun emptyAndArrayEpisodeResponsesAreSupported() {
        assertTrue(parseEpisodes(JSONObject("""{"episodes":[]}""")).isEmpty())
        val rows = parseEpisodes(JSONObject("""{"episodes":[{"id":4,"season":3,"episode_num":1,"title":"Pilote","container_extension":"../bad"}]}"""))
        assertEquals(3, rows.single().season)
        assertEquals("Pilote", rows.single().title)
        assertEquals("mp4", rows.single().extension)
    }
}
