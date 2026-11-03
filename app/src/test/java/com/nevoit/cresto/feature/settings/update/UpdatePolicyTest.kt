package com.nevoit.cresto.feature.settings.update

import com.nevoit.cresto.feature.settings.update.UpdatePolicy.Problem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val appId = "com.sure.cresto"
    private val base = "https://github.com/Sure-Will/Cresto-Releases/releases"

    private fun manifest(
        applicationId: String? = appId,
        versionName: String = "1.0",
        versionCode: Int = 1085,
        minSdk: Int? = 30,
        downloadUrl: String = "$base/download/v1.0-1085/Cresto-1.0-1085.apk",
        apkSize: Long? = 30_000_000,
        apkSha256: String? = "a".repeat(64),
        releaseUrl: String? = "$base/tag/v1.0-1085",
        schemaVersion: Int = 1,
    ) = UpdateManifest(
        schemaVersion = schemaVersion,
        applicationId = applicationId,
        versionCode = versionCode,
        versionName = versionName,
        minSdk = minSdk,
        downloadUrl = downloadUrl,
        apkSize = apkSize,
        apkSha256 = apkSha256,
        releaseUrl = releaseUrl,
    )

    @Test
    fun acceptsReleaseManifest() {
        assertNull(UpdatePolicy.problem(manifest(), appId))
    }

    @Test
    fun decodesManifestWrittenByReleaseScript() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<UpdateManifest>(
            """
            {
              "schemaVersion": 1,
              "platform": "android",
              "channel": "stable",
              "applicationId": "com.sure.cresto",
              "versionCode": 1085,
              "versionName": "1.0",
              "minSdk": 30,
              "publishedAt": "2026-11-03T08:00:00Z",
              "downloadUrl": "$base/download/v1.0-1085/Cresto-1.0-1085.apk",
              "apkSize": 30000000,
              "apkSha256": "${"b".repeat(64)}",
              "releaseUrl": "$base/tag/v1.0-1085",
              "description": {"zh": "说明"},
              "releaseNotes": {"zh": [{"type": "fix", "title": "修复", "items": ["一条"]}]}
            }
            """.trimIndent()
        )
        assertNull(UpdatePolicy.problem(decoded, appId))
        assertEquals(listOf("一条"), decoded.localizedReleaseNotes("zh-CN").single().items)
    }

    @Test
    fun rejectsForeignAppAndSchema() {
        assertEquals(Problem.ForeignApp, UpdatePolicy.problem(manifest(applicationId = "com.nevoit.cresto"), appId))
        assertEquals(Problem.ForeignApp, UpdatePolicy.problem(manifest(applicationId = null), appId))
        assertEquals(Problem.Schema, UpdatePolicy.problem(manifest(schemaVersion = 2), appId))
    }

    @Test
    fun rejectsUntrustedOrMismatchedUrls() {
        val bad = listOf(
            "http://github.com/Sure-Will/Cresto-Releases/releases/download/v1.0-1085/Cresto.apk",
            "https://evil.com/Sure-Will/Cresto-Releases/releases/download/v1.0-1085/Cresto.apk",
            "https://github.com/Nevodev/Cresto/releases/download/v1.0-1085/Cresto.apk",
            "$base/download/latest/Cresto-1.0-1085.apk",
            "$base/download/v1.0-1085/Cresto-1.0-1085.apk?x=1",
            "$base/download/v1.0-1084/Cresto-1.0-1085.apk",
            "$base/download/v1.0-1085/Cresto-1.0-1085.zip",
        )
        bad.forEach { url ->
            assertEquals(url, Problem.Source, UpdatePolicy.problem(manifest(downloadUrl = url), appId))
        }
        assertEquals(Problem.Source, UpdatePolicy.problem(manifest(releaseUrl = null), appId))
        assertNull(UpdatePolicy.apkTag("$base/download/latest/x.apk"))
    }

    @Test
    fun rejectsBadPackageFacts() {
        assertEquals(Problem.Package, UpdatePolicy.problem(manifest(apkSha256 = "xyz"), appId))
        assertEquals(Problem.Package, UpdatePolicy.problem(manifest(apkSha256 = "A".repeat(64)), appId))
        assertEquals(Problem.Package, UpdatePolicy.problem(manifest(apkSha256 = null), appId))
        assertEquals(Problem.Package, UpdatePolicy.problem(manifest(apkSize = 0), appId))
        assertEquals(Problem.Package, UpdatePolicy.problem(manifest(apkSize = 200L * 1024 * 1024), appId))
        assertEquals(Problem.Version, UpdatePolicy.problem(manifest(versionName = "1.0\n0"), appId))
        assertEquals(Problem.Version, UpdatePolicy.problem(manifest(versionCode = 0), appId))
        assertEquals(Problem.Version, UpdatePolicy.problem(manifest(minSdk = null), appId))
    }

    @Test
    fun upgradeRequiresHigherVersionAndSupportedSdk() {
        val m = manifest()
        assertTrue(UpdatePolicy.isUpgrade(m, installedVersionCode = 1084, sdkInt = 30))
        assertFalse(UpdatePolicy.isUpgrade(m, installedVersionCode = 1085, sdkInt = 37))
        assertFalse(UpdatePolicy.isUpgrade(m, installedVersionCode = 1084, sdkInt = 29))
    }
}
