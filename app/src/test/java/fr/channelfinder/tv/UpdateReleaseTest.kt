package fr.channelfinder.tv

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private val packageName = "fr.channelfinder.tv.salontest"
    private fun manifest(): JSONObject = JSONObject().put("packageName", packageName).put("versionCode", 12)
        .put("versionName", "0.2.1-salon-test9").put("size", 26_000_000).put("minSdk", 23)
        .put("sha256", "a".repeat(64)).put("apkUrl", "${RELEASE_ROOT}download/salon-test-20261018/canape-salon-google-tv-test.apk")

    @Test fun `release channels stay separate`() {
        assertTrue(updateManifestUrl(packageName).endsWith("update-salon.json"))
        assertTrue(updateManifestUrl("fr.channelfinder.tv").endsWith("latest/download/update.json"))
        assertThrows(IllegalStateException::class.java) { updateManifestUrl("other.app") }
        assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(manifest().toString(), "fr.channelfinder.tv") }
    }

    @Test fun `valid newer metadata and matching signing identity accepted`() {
        val release = UpdateRelease.parse(manifest().toString(), packageName)
        assertTrue(matchingUpdateIdentity(release, packageName, 11, packageName, 12, setOf("signer"), setOf("signer")))
        assertFalse(matchingUpdateIdentity(release, packageName, 12, packageName, 12, setOf("signer"), setOf("signer")))
        assertFalse(matchingUpdateIdentity(release, packageName, 11, packageName, 13, setOf("signer"), setOf("signer")))
        assertFalse(matchingUpdateIdentity(release, packageName, 11, "other.app", 12, setOf("signer"), setOf("signer")))
        assertFalse(matchingUpdateIdentity(release, packageName, 11, packageName, 12, setOf("signer"), setOf("attacker")))
        assertFalse(matchingUpdateIdentity(release, packageName, 11, packageName, 12, emptySet(), emptySet()))
    }

    @Test fun `reject untrusted release URLs and path traversal`() {
        listOf("http://github.com/anthonymq/canape/releases/download/v1/app.apk",
            "https://evil.test/app.apk", "${RELEASE_ROOT}download/v1/../../app.apk",
            "${RELEASE_ROOT}download/v1/%2e%2e.apk", "${RELEASE_ROOT}download/v1/app.apk?other=1",
            "https://github.com/other/repo/releases/download/v1/app.apk").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(manifest().put("apkUrl", url).toString(), packageName) }
        }
    }

    @Test fun `reject broken hashes oversized files and invalid version metadata`() {
        listOf("sha256" to "bad", "size" to 0, "size" to MAX_UPDATE_BYTES + 1, "versionCode" to 0,
            "versionName" to "", "minSdk" to 0).forEach { (field, value) ->
            assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(manifest().put(field, value).toString(), packageName) }
        }
    }

    @Test fun `redirects require HTTPS official hosts and no userinfo or custom port`() {
        assertTrue(trustedUpdateUrl("https://release-assets.githubusercontent.com/path?token=example"))
        listOf("http://github.com/a", "https://github.com.evil.test/a", "https://github.com@evil.test/a",
            "https://user@github.com/a", "https://github.com:444/a", "file:///tmp/a", "https://github.com/a#fragment").forEach {
            assertFalse(it, trustedUpdateUrl(it))
        }
    }
}
