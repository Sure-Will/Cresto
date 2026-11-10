package com.nevoit.cresto.data.learning

import com.nevoit.cresto.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

enum class StudyLogSyncError {
    NotConfigured,
    InvalidSource,
    Unauthorized,
    NoWriteAccess,
    RateLimited,
    NotFound,
    Network,
    Server
}

sealed interface StudyLogPushResult {
    /** [sha] identifies the uploaded file; replacing it next time needs it. */
    data class Success(val sha: String) : StudyLogPushResult
    data class Failure(val error: StudyLogSyncError) : StudyLogPushResult
}

/** Creates or replaces one file in a GitHub repository through the contents API. */
object StudyLogRemote {
    const val COMMIT_MESSAGE = "docs: sync Cresto study log"

    private const val TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

    data class Response(val status: Int, val body: String, val rateLimitRemaining: String?)

    /** One blocking HTTP exchange; throws [IOException] when the network fails. */
    fun interface Transport {
        fun send(method: String, url: String, token: String, body: String?): Response
    }

    private sealed interface Lookup {
        data class Found(val sha: String) : Lookup
        data object Missing : Lookup
        data class Failed(val error: StudyLogSyncError) : Lookup
    }

    private sealed interface Write {
        data class Written(val sha: String) : Write
        data object Conflict : Write
        data class Failed(val error: StudyLogSyncError) : Write
    }

    /**
     * Blocking; call from an IO dispatcher. Uses [knownSha] from the last upload when given, and
     * looks the file up again once if GitHub says it changed since. The token is never logged.
     */
    fun push(
        repository: String,
        path: String,
        token: String,
        markdown: String,
        knownSha: String?,
        transport: Transport = HttpTransport
    ): StudyLogPushResult {
        val url = StudyPlanRemote.contentsUrl(repository, path)
            ?: return StudyLogPushResult.Failure(StudyLogSyncError.InvalidSource)
        val trimmedToken = token.trim()
        if (trimmedToken.isEmpty()) return StudyLogPushResult.Failure(StudyLogSyncError.NotConfigured)

        return try {
            var sha = knownSha
            if (sha == null) {
                when (val found = lookup(url, trimmedToken, transport)) {
                    is Lookup.Found -> sha = found.sha
                    Lookup.Missing -> Unit
                    is Lookup.Failed -> return StudyLogPushResult.Failure(found.error)
                }
            }
            when (val first = write(url, trimmedToken, markdown, sha, transport)) {
                is Write.Written -> StudyLogPushResult.Success(first.sha)
                is Write.Failed -> StudyLogPushResult.Failure(first.error)
                Write.Conflict -> {
                    // Someone else changed or removed the file. Cresto owns it, so replace it.
                    val current = when (val found = lookup(url, trimmedToken, transport)) {
                        is Lookup.Found -> found.sha
                        Lookup.Missing -> null
                        is Lookup.Failed -> return StudyLogPushResult.Failure(found.error)
                    }
                    when (val second = write(url, trimmedToken, markdown, current, transport)) {
                        is Write.Written -> StudyLogPushResult.Success(second.sha)
                        is Write.Failed -> StudyLogPushResult.Failure(second.error)
                        Write.Conflict -> StudyLogPushResult.Failure(StudyLogSyncError.Server)
                    }
                }
            }
        } catch (_: IOException) {
            StudyLogPushResult.Failure(StudyLogSyncError.Network)
        }
    }

    /** JSON body of a contents API PUT; [sha] is required when the file already exists. */
    fun requestBody(markdown: String, sha: String?): String =
        buildJsonObject {
            put("message", COMMIT_MESSAGE)
            put("content", Base64.getEncoder().encodeToString(markdown.toByteArray(Charsets.UTF_8)))
            if (sha != null) put("sha", sha)
        }.toString()

    /** Maps a failed status to what the user needs to fix. 409 and 422 mean the SHA was stale. */
    fun classify(statusCode: Int, rateLimitRemaining: String?): StudyLogSyncError? {
        return when {
            statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> StudyLogSyncError.Unauthorized
            statusCode == 429 -> StudyLogSyncError.RateLimited
            statusCode == HttpURLConnection.HTTP_FORBIDDEN && rateLimitRemaining == "0" -> {
                StudyLogSyncError.RateLimited
            }

            // A read-only fine-grained token gets 403 when it tries to write.
            statusCode == HttpURLConnection.HTTP_FORBIDDEN -> StudyLogSyncError.NoWriteAccess
            statusCode == HttpURLConnection.HTTP_NOT_FOUND -> StudyLogSyncError.NotFound
            statusCode == HttpURLConnection.HTTP_CONFLICT || statusCode == 422 -> null
            else -> StudyLogSyncError.Server
        }
    }

    private fun lookup(url: String, token: String, transport: Transport): Lookup {
        val response = transport.send("GET", url, token, null)
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) return Lookup.Missing
        if (response.status !in 200..299) {
            return Lookup.Failed(
                classify(response.status, response.rateLimitRemaining) ?: StudyLogSyncError.Server
            )
        }
        // A JSON array means the path is a directory.
        val sha = parseObject(response.body)?.stringAt("sha")
            ?: return Lookup.Failed(StudyLogSyncError.InvalidSource)
        return Lookup.Found(sha)
    }

    private fun write(
        url: String,
        token: String,
        markdown: String,
        sha: String?,
        transport: Transport
    ): Write {
        val response = transport.send("PUT", url, token, requestBody(markdown, sha))
        if (response.status in 200..299) {
            val written = (parseObject(response.body)?.get("content") as? JsonObject)?.stringAt("sha")
                ?: return Write.Failed(StudyLogSyncError.Server)
            return Write.Written(written)
        }
        return when (val error = classify(response.status, response.rateLimitRemaining)) {
            null -> Write.Conflict
            else -> Write.Failed(error)
        }
    }

    private fun parseObject(body: String): JsonObject? =
        runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonObject.stringAt(key: String): String? =
        runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private object HttpTransport : Transport {
        override fun send(method: String, url: String, token: String, body: String?): Response {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.requestMethod = method
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Cresto/${BuildConfig.VERSION_NAME}")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use(::readLimited).orEmpty()
                return Response(status, text, connection.getHeaderField("x-ratelimit-remaining"))
            } finally {
                connection.disconnect()
            }
        }

        private fun readLimited(input: InputStream): String {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            while (buffer.size() <= MAX_RESPONSE_BYTES) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            return buffer.toString(Charsets.UTF_8.name())
        }
    }
}
