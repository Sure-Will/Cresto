package com.nevoit.cresto.feature.settings.update

import android.os.Build
import com.nevoit.cresto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val TIMEOUT_MS = 5_000
    private const val MAX_MANIFEST_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) return@withContext UpdateCheckResult.NotConfigured

        runCatching {
            // Nothing has been published yet, so there is nothing newer either.
            val manifest = fetchManifest(manifestUrl) ?: return@runCatching UpdateCheckResult.NoUpdate
            UpdatePolicy.problem(manifest, BuildConfig.APPLICATION_ID)?.let { problem ->
                error("Untrusted update manifest: $problem")
            }
            val currentVersionCode = BuildConfig.VERSION_CODE
            val hasUpdate = UpdatePolicy.isUpgrade(manifest, currentVersionCode, Build.VERSION.SDK_INT)
            if (!hasUpdate) {
                UpdateCheckResult.NoUpdate
            } else {
                val isUnsupported = manifest.minSupportedVersionCode?.let {
                    currentVersionCode < it
                } ?: false
                UpdateCheckResult.HasUpdate(
                    UpdateInfo(
                        manifest = manifest,
                        isRequired = manifest.forceUpdate || isUnsupported
                    )
                )
            }
        }.getOrElse { throwable ->
            UpdateCheckResult.Failed(throwable)
        }
    }

    private fun fetchManifest(manifestUrl: String): UpdateManifest? {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Cresto/${BuildConfig.VERSION_NAME}")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (responseCode !in 200..299) {
                error("Update manifest request failed: HTTP $responseCode")
            }
            val body = connection.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > MAX_MANIFEST_BYTES) error("Update manifest is too large")
                }
                buffer.toString(Charsets.UTF_8.name())
            }
            json.decodeFromString<UpdateManifest>(body)
        } finally {
            connection.disconnect()
        }
    }
}
