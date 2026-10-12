package fr.channelfinder.tv

import android.net.Uri
import android.os.CancellationSignal
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
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

class XtreamRepository {
    fun loadCatalog(account: XcAccount, onStep: (LoadStep) -> Unit = {}): XtreamCatalog = translateErrors {
        onStep(LoadStep.CONNECTING)
        val categories = getArray(account, "get_live_categories") { onStep(LoadStep.CATEGORIES) }
        val categoryList = ArrayList<ChannelCategory>(categories.length())
        val categoryNames = HashMap<String, String>(categories.length())
        for (i in 0 until categories.length()) {
            val item = categories.optJSONObject(i) ?: continue
            val id = item.optString("category_id")
            val name = item.optString("category_name", "Autres").trim()
            if (id.isNotBlank()) {
                categoryNames[id] = name
                categoryList += ChannelCategory(id = id, name = name)
            }
        }
        val streams = getArray(account, "get_live_streams") { onStep(LoadStep.CHANNELS) }
        onStep(LoadStep.PREPARING)
        val result = ArrayList<Channel>(streams.length())
        for (i in 0 until streams.length()) {
            val item = streams.optJSONObject(i) ?: continue
            val id = item.optString("stream_id")
            val name = item.optString("name").trim()
            if (id.isBlank() || name.isBlank() || ChannelText.isSeparator(name)) continue
            val categoryId = item.optString("category_id")
            val category = categoryNames[categoryId] ?: "Autres"
            result += Channel(
                id = id,
                name = name,
                categoryId = categoryId,
                category = category,
                logoUrl = item.optString("stream_icon").trim(),
                epgId = item.optString("epg_channel_id").let { if (it == "null") "" else it },
                lane = ChannelText.classifyLane(category, name)
            )
        }
        if (result.isEmpty()) throw CatalogException("Le compte est valide, mais le fournisseur ne propose aucune chaîne en direct.")
        XtreamCatalog(categories = categoryList, channels = result)
    }

    fun streamUrl(account: XcAccount, channel: Channel): Uri = Uri.parse(
        "${account.serverUrl.trimEnd('/')}/live/${Uri.encode(account.username)}/${Uri.encode(account.password)}/${channel.id}.ts"
    )

    private fun getArray(account: XcAccount, action: String, onConnected: () -> Unit): JSONArray {
        val json = request(account, action, onConnected = onConnected).trim()
        if (json.startsWith("{")) {
            val accountInfo = JSONObject(json).optJSONObject("user_info")
            if (accountInfo?.optInt("auth", 1) == 0) throw CatalogException("Utilisateur ou mot de passe refusé par le fournisseur.")
            throw CatalogException("Réponse inattendue du fournisseur. Vérifiez qu’il s’agit bien d’une adresse Xtream.")
        }
        if (json.isEmpty()) throw CatalogException("Utilisateur ou mot de passe refusé par le fournisseur.")
        return JSONArray(json)
    }

    fun loadGuide(account: XcAccount, channelId: String, cancellation: CancellationSignal): List<TvProgramme> = translateErrors {
        parseGuide(request(account, "get_simple_data_table", "&stream_id=${enc(channelId)}", guide = true, cancellation = cancellation))
    }

    private fun request(account: XcAccount, action: String, extra: String = "", guide: Boolean = false,
                        cancellation: CancellationSignal? = null, onConnected: () -> Unit = {}): String {
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
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use {
                if (guide) {
                    val text = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        if (text.length + count > 2_000_000) throw CatalogException("Le guide reçu est trop volumineux. Réessayez plus tard.")
                        text.append(buffer, 0, count)
                    }
                    text.toString()
                } else it.readText()
            }.orEmpty()
            when {
                status == 401 || status == 403 -> throw CatalogException("Utilisateur ou mot de passe refusé par le fournisseur.")
                status == 404 -> throw CatalogException("Ce serveur ne ressemble pas à un service Xtream. Vérifiez l’adresse et le port.")
                status >= 500 -> throw CatalogException("Le fournisseur rencontre un problème (erreur $status). Réessayez dans quelques minutes.")
                status !in 200..299 -> throw CatalogException("Le fournisseur a refusé la demande (erreur $status).")
            }
            return body
        } finally {
            connection.disconnect()
            cancellation?.setOnCancelListener(null)
        }
    }

    private inline fun <T> translateErrors(block: () -> T): T = try {
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
        throw CatalogException("Réponse illisible du fournisseur. Vérifiez qu’il s’agit bien d’une adresse Xtream.")
    } catch (error: IOException) {
        throw CatalogException("La connexion a été interrompue. Vérifiez le réseau, puis réessayez.")
    } catch (error: IllegalArgumentException) {
        throw CatalogException("Adresse du serveur invalide.")
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
