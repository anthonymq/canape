package fr.channelfinder.tv

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class RemoteSettingsServerTest {
    @Test fun remoteFormRequiresSessionAndValidAccount() {
        val actions = mutableListOf<Map<String, String>>()
        val server = RemoteSettingsServer(dispatch = { it() }, action = { actions.add(it) })
        server.start(1000, true)
        try {
            fun request(path: String, body: String? = null): Pair<Int, String> {
                val connection = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                return try {
                    val code = connection.responseCode
                    code to (if (code >= 400) connection.errorStream else connection.inputStream).bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
            }
            assertEquals(404, request("/wrong").first)
            val form = request(server.path)
            assertEquals(200, form.first)
            assertTrue(form.second.contains("type=\"password\""))
            assertEquals(400, request(server.path, "action=account&server=ftp://example.invalid&username=test&password=test").first)
            assertTrue(actions.isEmpty())
            assertEquals(200, request(server.path, "action=account&server=https://example.invalid&username=test&password=test").first)
            assertEquals("account", actions.single()["action"])
            val audio = request(server.path, "action=audio&language=pt")
            assertTrue(audio.second.contains("value=\"pt\" selected"))
            assertTrue(request(server.path).second.contains("value=\"pt\" selected"))
            assertEquals(400, request(server.path, "action=unknown").first)
            assertEquals(400, request(server.path, "action=account&server=" + "a".repeat(9000)).first)
        } finally { server.close() }
    }
}
