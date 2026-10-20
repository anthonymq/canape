package fr.channelfinder.tv

import org.json.JSONObject
import java.net.URI

internal const val MAX_UPDATE_BYTES = 200L * 1024 * 1024
internal const val RELEASE_ROOT = "https://github.com/anthonymq/canape/releases/"

internal fun updateManifestUrl(packageName: String): String = when (packageName) {
    "fr.channelfinder.tv.salontest" -> "${RELEASE_ROOT}download/salon-test-20261018/update-salon.json"
    "fr.channelfinder.tv" -> "${RELEASE_ROOT}latest/download/update.json"
    else -> error("Canal de mise à jour inconnu")
}

internal data class UpdateRelease(val packageName: String, val versionCode: Long, val versionName: String,
    val apkUrl: String, val sha256: String, val size: Long, val minSdk: Int) {
    companion object {
        fun parse(json: String, installedPackage: String): UpdateRelease {
            val value = JSONObject(json)
            val result = UpdateRelease(value.getString("packageName"), value.getLong("versionCode"),
                value.getString("versionName"), value.getString("apkUrl"), value.getString("sha256"),
                value.getLong("size"), value.getInt("minSdk"))
            require(result.packageName == installedPackage)
            require(result.versionCode > 0 && result.versionName.isNotBlank() && result.versionName.length <= 80)
            require(result.size in 1..MAX_UPDATE_BYTES && result.minSdk >= 23)
            require(result.sha256.matches(Regex("[a-fA-F0-9]{64}")))
            require(result.apkUrl.startsWith("${RELEASE_ROOT}download/"))
            val uri = URI(result.apkUrl)
            require(uri.rawPath.matches(Regex("/anthonymq/canape/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk")))
            require(uri.query == null && trustedUpdateUrl(result.apkUrl))
            return result
        }
    }
}

internal fun trustedUpdateUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.fragment == null &&
        uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}.getOrDefault(false)

internal fun matchingUpdateIdentity(expected: UpdateRelease, installedPackage: String, installedVersion: Long,
    archivePackage: String?, archiveVersion: Long, installedSigners: Set<String>, archiveSigners: Set<String>): Boolean =
    expected.packageName == installedPackage && archivePackage == installedPackage &&
        archiveVersion == expected.versionCode && archiveVersion > installedVersion &&
        installedSigners.isNotEmpty() && installedSigners == archiveSigners
