package fr.channelfinder.tv

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

private const val TAG = "CatalogCache"
private const val MAGIC = 0x43465443 // "CFTC"
private const val VERSION = 2

/**
 * Last successful catalog on internal storage, so Home opens instantly and refreshes in the background.
 * Holds channel metadata only: no credentials or stream URLs. The account is identified by a hash.
 * A compact binary format keeps reading ~24k channels well under a second on TV hardware.
 */
class CatalogCache(context: Context) {
    data class Entry(val catalog: XtreamCatalog, val savedAt: Long)

    private val file = File(context.filesDir, "catalog_cache.bin")

    init {
        File(context.filesDir, "catalog_cache.json").delete() // earlier JSON format
    }

    fun load(account: XcAccount): Entry? {
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                if (input.readUTF() != accountKey(account)) return null
                val savedAt = input.readLong()
                val categoryCount = input.readInt()
                val categories = ArrayList<ChannelCategory>(categoryCount)
                val names = HashMap<String, String>(categoryCount * 2)
                repeat(categoryCount) {
                    val category = ChannelCategory(input.readUTF(), input.readUTF())
                    categories += category
                    names[category.id] = category.name
                }
                val channelCount = input.readInt()
                val channels = ArrayList<Channel>(channelCount)
                repeat(channelCount) {
                    val id = input.readUTF()
                    val name = input.readUTF()
                    val categoryId = input.readUTF()
                    channels += Channel(
                        id = id,
                        name = name,
                        categoryId = categoryId,
                        category = names[categoryId] ?: "Autres",
                        logoUrl = input.readUTF(),
                        epgId = input.readUTF(),
                        lane = input.readUTF()
                    )
                }
                Entry(XtreamCatalog(categories, channels), savedAt)
            }
        }.onFailure { Log.w(TAG, "Ignoring unreadable catalog cache: ${it.javaClass.simpleName}") }.getOrNull()
    }

    fun save(account: XcAccount, catalog: XtreamCatalog) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            DataOutputStream(BufferedOutputStream(temp.outputStream(), 1 shl 16)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(accountKey(account))
                out.writeLong(System.currentTimeMillis())
                out.writeInt(catalog.categories.size)
                catalog.categories.forEach { out.writeUTF(it.id); out.writeUTF(it.name) }
                out.writeInt(catalog.channels.size)
                catalog.channels.forEach {
                    out.writeUTF(it.id)
                    out.writeUTF(it.name)
                    out.writeUTF(it.categoryId)
                    out.writeUTF(it.logoUrl)
                    out.writeUTF(it.epgId)
                    out.writeUTF(it.lane)
                }
            }
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { Log.w(TAG, "Could not write catalog cache: ${it.javaClass.simpleName}") }
    }

    fun clear() {
        file.delete()
    }

    private fun accountKey(account: XcAccount): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${account.serverUrl}\n${account.username}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
