package fr.channelfinder.tv

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

internal class LibraryCache(private val directory: File, private val maxBytes: Long = 128L * 1024 * 1024) {
    data class Entry(val catalog: LibraryCatalog, val savedAt: Long) {
        fun isFresh(now: Long = System.currentTimeMillis()): Boolean = catalog.items.isNotEmpty() &&
            now >= savedAt && now - savedAt < 6 * 60 * 60 * 1000L
    }

    @Synchronized
    fun load(account: XcAccount, scope: String): Entry? {
        val file = file(account, scope)
        if (!file.isFile || file.length() > MAX_ENTRY_BYTES) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val savedAt = input.readLong()
                val categories = List(input.count(50_000)) { ChannelCategory(input.text(), input.text()) }
                val items = List(input.count(250_000)) {
                    LibraryItem(input.text(), input.text(), input.text(), input.text(), input.text(),
                        input.readInt(), input.readInt(), input.text(), input.text(), input.text(), input.text())
                }
                if (input.read() != -1) return null
                file.setLastModified(System.currentTimeMillis())
                Entry(LibraryCatalog(categories, items), savedAt)
            }
        } catch (_: IOException) { null
        } catch (_: IllegalArgumentException) { null }
    }

    @Synchronized
    fun save(account: XcAccount, scope: String, catalog: LibraryCatalog, savedAt: Long = System.currentTimeMillis()): Boolean {
        if (catalog.categories.size > 50_000 || catalog.items.size > 250_000) return false
        val file = file(account, scope)
        val temp = File(directory, "${file.name}.tmp")
        return try {
            if (!directory.isDirectory && !directory.mkdirs()) return false
            DataOutputStream(BufferedOutputStream(BoundedCacheOutput(temp.outputStream(), minOf(MAX_ENTRY_BYTES, maxBytes)))).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeLong(savedAt)
                output.writeInt(catalog.categories.size)
                catalog.categories.forEach { output.text(it.id); output.text(it.name) }
                output.writeInt(catalog.items.size)
                catalog.items.forEach {
                    output.text(it.id); output.text(it.title); output.text(it.categoryId)
                    output.text(it.artwork); output.text(it.extension)
                    output.writeInt(it.season); output.writeInt(it.episode)
                    output.text(it.overview); output.text(it.year); output.text(it.rating); output.text(it.backdrop)
                }
            }
            if (temp.length() > minOf(MAX_ENTRY_BYTES, maxBytes) || !temp.renameTo(file)) return false
            var total = directory.listFiles()?.filter { it.extension == "bin" }?.sumOf { it.length() } ?: 0L
            directory.listFiles()?.filter { it.extension == "bin" && it != file }
                ?.sortedWith(compareBy<File> { it.name.startsWith("catalog-") }.thenBy { it.lastModified() })
                ?.forEach { candidate ->
                    if (total > maxBytes) {
                        val size = candidate.length()
                        if (candidate.delete()) total -= size
                    }
                }
            true
        } catch (_: IOException) { false
        } catch (_: IllegalArgumentException) { false
        } finally { temp.delete() }
    }

    private fun file(account: XcAccount, scope: String): File {
        val owner = digest("${account.serverUrl.trimEnd('/')}\n${account.username}\n${account.password}")
        val prefix = if (scope.startsWith("catalog:")) "catalog" else "entry"
        return File(directory, "$prefix-$owner-${digest(scope)}.bin")
    }

    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }
    private fun DataInputStream.text(): String {
        val size = count(512 * 1024)
        require(size <= available())
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }
    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 512 * 1024)
        writeInt(bytes.size)
        write(bytes)
    }
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAGIC = 0x43564F44
        const val VERSION = 1
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    }
}

private class BoundedCacheOutput(private val output: OutputStream, private val limit: Long) : OutputStream() {
    private var written = 0L
    override fun write(value: Int) {
        if (written >= limit) throw IOException("Cache entry too large")
        output.write(value)
        written++
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > limit - written) throw IOException("Cache entry too large")
        output.write(bytes, offset, length)
        written += length
    }
    override fun flush() = output.flush()
    override fun close() = output.close()
}
