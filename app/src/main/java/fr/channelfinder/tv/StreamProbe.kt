package fr.channelfinder.tv

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Debug-only diagnostics: checks which streams in the France and Português lanes actually answer.
 * Run with `adb shell am start -S -n fr.channelfinder.tv/.MainActivity -e probe 30` (count[:gapMs[:first]])
 * and read `adb logcat -s StreamProbe`. Logs channel names,
 * status codes and timings only, never URLs or credentials. Probes run one at a time so the
 * provider's connection limit is respected.
 */
object StreamProbe {
    private const val TAG = "StreamProbe"

    fun accountInfo(account: XcAccount) {
        runCatching {
            val url = "${account.serverUrl.trimEnd('/')}/player_api.php?username=${enc(account.username)}&password=${enc(account.password)}"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val info = JSONObject(body).optJSONObject("user_info")
            Log.i(TAG, "account status=${info?.optString("status")} max_connections=${info?.optString("max_connections")} " +
                "active_cons=${info?.optString("active_cons")} formats=${info?.optJSONArray("allowed_output_formats")}")
        }.onFailure { Log.i(TAG, "account info failed: ${it.javaClass.simpleName}") }
    }

    var gapMs = 400L
    fun probe(account: XcAccount, label: String, channels: List<Channel>) {
        var ok = 0
        val failures = mutableMapOf<String, Int>()
        channels.forEachIndexed { index, channel ->
            val ts = check(account, channel, "ts")
            val result = if (ts == "OK") ts else "$ts | m3u8=${check(account, channel, "m3u8")}"
            if (ts == "OK") ok++ else failures[ts] = (failures[ts] ?: 0) + 1
            Log.i(TAG, "$label ${index + 1}/${channels.size} ts=$result name=${channel.name}")
            Thread.sleep(gapMs)
        }
        Log.i(TAG, "$label SUMMARY ok=$ok/${channels.size} failures=$failures")
    }

    /** Returns "OK" when the stream starts sending data, otherwise a short reason. */
    private fun check(account: XcAccount, channel: Channel, extension: String): String {
        var url = "${account.serverUrl.trimEnd('/')}/live/${enc(account.username)}/${enc(account.password)}/${channel.id}.$extension"
        val started = System.currentTimeMillis()
        repeat(6) {
            val connection = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (error: Exception) {
                return "bad-url"
            }
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "VLC/3.0.21 LibVLC/3.0.21")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), connection.getHeaderField("Location") ?: return "redirect-no-location").toString()
                    return@repeat
                }
                if (code !in 200..299) return "http$code"
                val buffer = ByteArray(1024)
                val read = connection.inputStream.read(buffer)
                val ms = System.currentTimeMillis() - started
                return when {
                    read <= 0 -> "empty"
                    extension == "ts" && buffer[0] != 0x47.toByte() -> "not-ts(${connection.contentType})"
                    else -> "OK"
                }.let { if (it == "OK" && ms > 4000) "SLOW${ms}ms" else it }
            } catch (error: Exception) {
                val detail = error.message.orEmpty().replace(Regex("""\S*://\S*"""), "<url>").take(80)
                return "${error.javaClass.simpleName}($detail)"
            } finally {
                connection.disconnect()
            }
        }
        return "too-many-redirects"
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
