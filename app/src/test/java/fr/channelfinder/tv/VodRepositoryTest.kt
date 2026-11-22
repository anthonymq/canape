package fr.channelfinder.tv

import java.io.StringReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VodRepositoryTest {
    private fun items(json: String, kind: LibraryKind) = parseLibraryItems(StringReader(json), kind)

    @Test fun moviesAndSeriesUseTheirOwnIdentifiers() {
        val json = """[{"stream_id":12,"series_id":34,"name":"Titre","container_extension":"mkv"},{"name":"Sans identifiant"}]"""
        assertEquals("12", items(json, LibraryKind.MOVIES).single().id)
        assertEquals("34", items(json, LibraryKind.SERIES).single().id)
        assertEquals("mkv", items(json, LibraryKind.MOVIES).single().extension)
    }

    @Test fun seriesRowsKeepMetadataAndSkipUnusableEntries() {
        val rows = items("""[
            {"series_id":1,"name":" Le Bureau ","category_id":5,"cover":"c.jpg","plot":"Résumé","year":"2015","rating":"8.4",
             "backdrop_path":["b1.jpg","b2.jpg"],"cast":{"nested":true}},
            {"series_id":1,"name":"Doublon"},
            {"series_id":2,"name":"Sans détails","plot":null,"year":"null","rating":"0","backdrop_path":[],"container_extension":"../x"},
            {"series_id":3,"name":"null"}, {"series_id":null,"name":"Sans identifiant"}, 7
        ]""", LibraryKind.SERIES)
        assertEquals(listOf("1", "2"), rows.map { it.id })
        assertEquals(LibraryItem("1", "Le Bureau", "5", "c.jpg", "mp4", overview = "Résumé", year = "2015", rating = "8.4", backdrop = "b1.jpg"), rows[0])
        assertEquals(LibraryItem("2", "Sans détails"), rows[1])
        assertEquals(UNREADABLE, assertThrows(CatalogException::class.java) { items("", LibraryKind.MOVIES) }.message)
        assertEquals(NOT_A_LIST, assertThrows(CatalogException::class.java) { items("""{"user_info":{"auth":1}}""", LibraryKind.MOVIES) }.message)
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
