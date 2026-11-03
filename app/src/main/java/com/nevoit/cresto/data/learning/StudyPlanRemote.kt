package com.nevoit.cresto.data.learning

import com.nevoit.cresto.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class StudyPlanSyncError {
    NotConfigured,
    InvalidSource,
    Unauthorized,
    Forbidden,
    RateLimited,
    NotFound,
    Network,
    Server,
    TooLarge,
    Format
}

sealed interface StudyPlanFetchResult {
    data class Success(val markdown: String) : StudyPlanFetchResult
    data class Failure(val error: StudyPlanSyncError) : StudyPlanFetchResult
}

/** Reads one file from a GitHub repository through the contents API. */
object StudyPlanRemote {
    private const val TIMEOUT_MS = 10_000
    private const val MAX_PLAN_BYTES = 1024 * 1024

    private val repositoryPattern = Regex("""^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$""")

    /** `https://api.github.com/repos/<owner>/<name>/contents/<path>`, or null when invalid. */
    fun contentsUrl(repository: String, path: String): String? {
        val repo = repository.trim()
        val segments = path.trim().trim('/').split('/')
        if (!repositoryPattern.matches(repo)) return null
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        val encodedPath = segments.joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "https://api.github.com/repos/$repo/contents/$encodedPath"
    }

    /** Maps a non-success HTTP status to what the user needs to fix. */
    fun classify(statusCode: Int, rateLimitRemaining: String?): StudyPlanSyncError {
        return when {
            statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> StudyPlanSyncError.Unauthorized
            statusCode == 429 -> StudyPlanSyncError.RateLimited
            statusCode == HttpURLConnection.HTTP_FORBIDDEN && rateLimitRemaining == "0" -> {
                StudyPlanSyncError.RateLimited
            }

            statusCode == HttpURLConnection.HTTP_FORBIDDEN -> StudyPlanSyncError.Forbidden
            // GitHub answers 404 for private repositories the token cannot see.
            statusCode == HttpURLConnection.HTTP_NOT_FOUND -> StudyPlanSyncError.NotFound
            else -> StudyPlanSyncError.Server
        }
    }

    /** Blocking; call from an IO dispatcher. The token is only sent, never logged or returned. */
    fun fetch(repository: String, path: String, token: String): StudyPlanFetchResult {
        val url = contentsUrl(repository, path)
            ?: return StudyPlanFetchResult.Failure(StudyPlanSyncError.InvalidSource)
        if (token.isBlank()) return StudyPlanFetchResult.Failure(StudyPlanSyncError.NotConfigured)

        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github.raw+json")
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Cresto/${BuildConfig.VERSION_NAME}")
            }
        } catch (_: IOException) {
            return StudyPlanFetchResult.Failure(StudyPlanSyncError.Network)
        }

        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                StudyPlanFetchResult.Failure(
                    classify(statusCode, connection.getHeaderField("x-ratelimit-remaining"))
                )
            } else {
                readBody(connection)
            }
        } catch (_: IOException) {
            StudyPlanFetchResult.Failure(StudyPlanSyncError.Network)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): StudyPlanFetchResult {
        connection.inputStream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > MAX_PLAN_BYTES) {
                    return StudyPlanFetchResult.Failure(StudyPlanSyncError.TooLarge)
                }
            }
            return StudyPlanFetchResult.Success(buffer.toString(Charsets.UTF_8.name()))
        }
    }
}
