package com.nevoit.cresto.feature.settings.update

import java.net.URI

/**
 * Trust rules for the update manifest (pure logic, covered by unit tests).
 *
 * The manifest is published with the latest release of the public [REPOSITORY]. Its APK must be an
 * asset of the same release tag, so a manifest can never point the installer somewhere else.
 */
object UpdatePolicy {
    const val REPOSITORY = "Sure-Will/Cresto-Releases"
    const val MAX_APK_BYTES = 128L * 1024 * 1024

    private const val SCHEMA_VERSION = 1
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val APK_PATH =
        Regex("/$REPOSITORY/releases/download/([A-Za-z0-9._+-]+)/([A-Za-z0-9._+-]+\\.apk)")
    private val RELEASE_PATH = Regex("/$REPOSITORY/releases/tag/([A-Za-z0-9._+-]+)")

    enum class Problem { Schema, ForeignApp, Version, Package, Source }

    /** Returns null when [manifest] describes a verifiable package for [applicationId]. */
    fun problem(manifest: UpdateManifest, applicationId: String): Problem? {
        if (manifest.schemaVersion != SCHEMA_VERSION) return Problem.Schema
        if (manifest.applicationId != applicationId) return Problem.ForeignApp
        if (manifest.versionCode <= 0 || !singleLine(manifest.versionName, 64)) return Problem.Version
        if (manifest.minSdk == null || manifest.minSdk !in 1..1000) return Problem.Version

        val size = manifest.apkSize
        val sha256 = manifest.apkSha256
        if (size == null || size !in 1..MAX_APK_BYTES) return Problem.Package
        if (sha256 == null || !SHA256.matches(sha256)) return Problem.Package

        val tag = apkTag(manifest.downloadUrl)
        if (tag == null || tag != manifest.releaseUrl?.let(::releaseTag)) return Problem.Source
        return null
    }

    fun isUpgrade(manifest: UpdateManifest, installedVersionCode: Int, sdkInt: Int): Boolean =
        manifest.versionCode > installedVersionCode && sdkInt >= (manifest.minSdk ?: Int.MAX_VALUE)

    /** Release tag of an APK asset in [REPOSITORY]; null for anything else, including `latest`. */
    fun apkTag(url: String): String? {
        val path = githubPath(url) ?: return null
        val tag = APK_PATH.matchEntire(path)?.groupValues?.get(1) ?: return null
        return tag.takeUnless { it.equals("latest", ignoreCase = true) }
    }

    fun releaseTag(url: String): String? {
        val path = githubPath(url) ?: return null
        return RELEASE_PATH.matchEntire(path)?.groupValues?.get(1)
    }

    private fun githubPath(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val trusted = uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null
        return if (trusted) uri.rawPath else null
    }

    private fun singleLine(value: String, max: Int): Boolean =
        value.isNotBlank() && value.length <= max && value.none { it == '\n' || it == '\r' || it == '\u0000' }
}
