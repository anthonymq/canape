package fr.channelfinder.tv

import android.net.Uri
import android.os.CancellationSignal
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.MalformedJsonException
import org.json.JSONException
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A failure the viewer can understand; [message] is shown as-is on screen. */
class CatalogException(message: String) : Exception(message)

enum class LoadStep(val label: String) {
    OPENING("Ouverture de vos chaînes…"),
    CONNECTING("Connexion au serveur…"),
    CATEGORIES("Chargement des catégories…"),
    CHANNELS("Chargement des chaînes…"),
    PREPARING("Préparation de l’accueil…")
}

internal const val ACCOUNT_REFUSED = "Utilisateur ou mot de passe refusé par le fournisseur."
internal const val NOT_A_LIST = "Réponse inattendue du fournisseur. Vérifiez qu’il s’agit bien d’une adresse Xtream."
internal const val UNREADABLE = "Réponse illisible du fournisseur. Vérifiez qu’il s’agit bien d’une adresse Xtream."

class XtreamRepository {
    fun loadCatalog(account: XcAccount, onStep: (LoadStep) -> Unit = {}): XtreamCatalog = translateErrors {
        onStep(LoadStep.CONNECTING)
        val categories = exchange(account, "get_live_categories", onConnected = { onStep(LoadStep.CATEGORIES) }, read = ::parseLiveCategories)
        val categoryNames = categories.associate { it.id to it.name }
        val channels = exchange(account, "get_live_streams", onConnected = { onStep(LoadStep.CHANNELS) }) { parseLiveStreams(it, categoryNames) }
        onStep(LoadStep.PREPARING)
        if (channels.isEmpty()) throw CatalogException("Le compte est valide, mais le fournisseur ne propose aucune chaîne en direct.")
        XtreamCatalog(categories = categories, channels = channels)
    }

    fun streamUrl(account: XcAccount, channel: Channel): Uri = Uri.parse(
        "${account.serverUrl.trimEnd('/')}/live/${Uri.encode(account.username)}/${Uri.encode(account.password)}/${channel.id}.ts"
    )

    fun loadGuide(account: XcAccount, channelId: String, cancellation: CancellationSignal): List<TvProgramme> = translateErrors {
        parseGuide(request(account, "get_simple_data_table", "&stream_id=${enc(channelId)}", guide = true, cancellation = cancellation))
    }

    internal fun request(account: XcAccount, action: String, extra: String = "", guide: Boolean = false,
                        cancellation: CancellationSignal? = null): String =
        exchange(account, action, extra, guide, cancellation) { reader ->
            if (guide) {
                val text = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (text.length + count > 2_000_000) throw CatalogException("Le guide reçu est trop volumineux. Réessayez plus tard.")
                    text.append(buffer, 0, count)
                }
                text.toString()
            } else reader.readText()
        }

    /** Sends one API request and hands the successful reply to [read] as it arrives. */
    private fun <T> exchange(account: XcAccount, action: String, extra: String = "", guide: Boolean = false,
                             cancellation: CancellationSignal? = null, onConnected: () -> Unit = {}, read: (Reader) -> T): T {
        val query = "username=${enc(account.username)}&password=${enc(account.password)}&action=${enc(action)}$extra"
        val connection = URL("${account.serverUrl.trimEnd('/')}/player_api.php?$query").openConnection() as HttpURLConnection
        cancellation?.setOnCancelListener { connection.disconnect() }
        connection.connectTimeout = 15_000
        connection.readTimeout = if (guide) 20_000 else 60_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "EasyIPTV/0.2")
        try {
            cancellation?.throwIfCanceled()
            val status = connection.responseCode
            onConnected()
            when {
                status == 401 || status == 403 -> throw CatalogException(ACCOUNT_REFUSED)
                status == 404 -> throw CatalogException("Ce serveur ne ressemble pas à un service Xtream. Vérifiez l’adresse et le port.")
                status >= 500 -> throw CatalogException("Le fournisseur rencontre un problème (erreur $status). Réessayez dans quelques minutes.")
                status !in 200..299 -> throw CatalogException("Le fournisseur a refusé la demande (erreur $status).")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use(read)
        } finally {
            connection.disconnect()
            cancellation?.setOnCancelListener(null)
        }
    }

    internal inline fun <T> translateErrors(block: () -> T): T = try {
        block()
    } catch (error: CatalogException) {
        throw error
    } catch (error: UnknownHostException) {
        throw CatalogException("Serveur introuvable. Vérifiez l’adresse et la connexion Internet de la TV.")
    } catch (error: SocketTimeoutException) {
        throw CatalogException("Le serveur met trop de temps à répondre. Réessayez dans un instant.")
    } catch (error: ConnectException) {
        throw CatalogException("Connexion impossible au serveur. Vérifiez l’adresse et le port.")
    } catch (error: SSLException) {
        throw CatalogException("Connexion sécurisée impossible. Essayez l’adresse en http:// si votre fournisseur l’indique.")
    } catch (error: JSONException) {
        throw CatalogException(UNREADABLE)
    } catch (error: IOException) {
        throw CatalogException("La connexion a été interrompue. Vérifiez le réseau, puis réessayez.")
    } catch (error: IllegalArgumentException) {
        throw CatalogException("Adresse du serveur invalide.")
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}

internal fun parseLiveCategories(source: Reader): List<ChannelCategory> = buildList {
    readProviderRows(source, setOf("category_id", "category_name")) { row ->
        val id = row["category_id"].orEmpty()
        if (id.isNotBlank()) add(ChannelCategory(id = id, name = row["category_name"]?.trim() ?: "Autres"))
    }
}

internal fun parseLiveStreams(source: Reader, categoryNames: Map<String, String>): List<Channel> = buildList {
    readProviderRows(source, setOf("stream_id", "name", "category_id", "stream_icon", "epg_channel_id")) { row ->
        val id = row["stream_id"].orEmpty()
        val name = row["name"].orEmpty().trim()
        if (id.isBlank() || name.isBlank() || ChannelText.isSeparator(name)) return@readProviderRows
        val categoryId = row["category_id"].orEmpty()
        val category = categoryNames[categoryId] ?: "Autres"
        add(Channel(
            id = id,
            name = name,
            categoryId = categoryId,
            category = category,
            logoUrl = row["stream_icon"].orEmpty().trim(),
            epgId = row["epg_channel_id"].orEmpty().let { if (it == "null") "" else it },
            lane = ChannelText.classifyLane(category, name)
        ))
    }
}

/**
 * Reads a provider list one object at a time, keeping only [fields] as text. A 24k-channel reply is never
 * held whole in memory. An object instead of a list is the provider's account answer.
 */
private fun readProviderRows(source: Reader, fields: Set<String>, onRow: (Map<String, String>) -> Unit) {
    val reader = JsonReader(source).apply { strictness = Strictness.LENIENT }
    try {
        val first = try { reader.peek() } catch (_: EOFException) { throw CatalogException(ACCOUNT_REFUSED) }
        when (first) {
            JsonToken.BEGIN_ARRAY -> Unit
            JsonToken.BEGIN_OBJECT -> throw CatalogException(if (authRefused(reader)) ACCOUNT_REFUSED else NOT_A_LIST)
            else -> throw CatalogException(UNREADABLE)
        }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            val row = HashMap<String, String>(fields.size * 2)
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name in fields) readText(reader)?.let { row[name] = it } else reader.skipValue()
            }
            reader.endObject()
            onRow(row)
        }
        reader.endArray()
    } catch (_: MalformedJsonException) {
        throw CatalogException(UNREADABLE)
    } catch (_: IllegalStateException) {
        throw CatalogException(UNREADABLE)
    }
}

private fun authRefused(reader: JsonReader): Boolean {
    var refused = false
    reader.beginObject()
    while (reader.hasNext()) {
        if (reader.nextName() != "user_info" || reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "auth") refused = readText(reader)?.toDoubleOrNull() == 0.0 else reader.skipValue()
        }
        reader.endObject()
    }
    return refused
}

/** A scalar as text (numbers keep their provider spelling); null and nested values are absent. */
private fun readText(reader: JsonReader): String? = when (reader.peek()) {
    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
    JsonToken.BOOLEAN -> reader.nextBoolean().toString()
    JsonToken.NULL -> { reader.nextNull(); null }
    else -> { reader.skipValue(); null }
}
