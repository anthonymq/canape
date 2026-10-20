package fr.channelfinder.tv

import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val account = XcAccount("https://cache-fixture.invalid", "fixture-viewer", "fixture-secret")
    private val catalog = LibraryCatalog(listOf(ChannelCategory("one", "Découvertes")),
        listOf(LibraryItem("one", "Le voyage éblouissant", "one", "cover", "mkv", 2, 3,
            "Résumé\n".repeat(12_000), "2025", "8.1", "backdrop")))

    @Test fun persistedCatalogSurvivesNewCacheInstanceAndAllMetadataRoundTrips() {
        val dir = folder.newFolder()
        assertTrue(LibraryCache(dir).save(account, "catalog:MOVIES", catalog, 1234))
        assertEquals(LibraryCache.Entry(catalog, 1234), LibraryCache(dir).load(account, "catalog:MOVIES"))
    }

    @Test fun accountsKindsEpisodesAndDetailsDoNotShareEntries() {
        val cache = LibraryCache(folder.newFolder())
        assertTrue(cache.save(account, "catalog:MOVIES", catalog))
        assertNull(cache.load(account.copy(password = "different-fixture-secret"), "catalog:MOVIES"))
        assertNull(cache.load(account.copy(username = "different-fixture-viewer"), "catalog:MOVIES"))
        assertNull(cache.load(account.copy(serverUrl = "https://other-fixture.invalid"), "catalog:MOVIES"))
        listOf("catalog:SERIES", "episodes:one", "movie:one").forEach { assertNull(cache.load(account, it)) }
        assertTrue(cache.save(account, "episodes:one", catalog))
        assertEquals(catalog, cache.load(account, "episodes:one")?.catalog)
    }

    @Test fun freshnessExpiresWithoutDiscardingTheCatalog() {
        val entry = LibraryCache.Entry(catalog, 1_000)
        assertTrue(entry.isFresh(1_000))
        assertTrue(entry.isFresh(1_000 + 6 * 60 * 60 * 1000L - 1))
        assertFalse(entry.isFresh(1_000 + 6 * 60 * 60 * 1000L))
        assertFalse(entry.isFresh(999))
        assertFalse(LibraryCache.Entry(LibraryCatalog(emptyList(), emptyList()), 1_000).isFresh(1_000))
        val cache = LibraryCache(folder.newFolder())
        cache.save(account, "catalog:MOVIES", catalog, 1)
        assertEquals(catalog, cache.load(account, "catalog:MOVIES")?.catalog)
    }

    @Test fun corruptAndOversizedCountsAreRejected() {
        val dir = folder.newFolder()
        val cache = LibraryCache(dir)
        cache.save(account, "catalog:MOVIES", catalog)
        val file = dir.listFiles()!!.single()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(cache.load(account, "catalog:MOVIES"))
        DataOutputStream(file.outputStream()).use {
            it.writeInt(0x43564F44); it.writeInt(1); it.writeLong(0); it.writeInt(Int.MAX_VALUE)
        }
        assertNull(cache.load(account, "catalog:MOVIES"))
    }

    @Test fun rejectedWritePreservesLastGoodEntryAndRemovesTemporaryFile() {
        val dir = folder.newFolder()
        val cache = LibraryCache(dir)
        cache.save(account, "catalog:MOVIES", catalog)
        assertFalse(cache.save(account, "catalog:MOVIES", catalog.copy(items = listOf(LibraryItem("two", "x".repeat(600_000))))))
        assertEquals(catalog, cache.load(account, "catalog:MOVIES")?.catalog)
        assertTrue(dir.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun boundedStorageEvictsDetailsBeforeCatalogs() {
        val dir = folder.newFolder()
        val small = catalog.copy(items = listOf(LibraryItem("one", "Le voyage")))
        val cache = LibraryCache(dir, 400)
        assertTrue(cache.save(account, "catalog:MOVIES", small))
        assertTrue(cache.save(account, "movie:one", small))
        assertTrue(cache.save(account, "episodes:one", small))
        assertTrue(dir.listFiles()!!.sumOf(File::length) <= 400)
        assertNotNull(cache.load(account, "catalog:MOVIES"))
        assertNotNull(cache.load(account, "episodes:one"))
    }
}
