package com.nevoit.cresto.data.learning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Base64

class StudyLogRemoteTest {

    private val url = "https://api.github.com/repos/owner/notes/contents/area/cresto-study-log.md"

    private data class Call(val method: String, val url: String, val body: String?)

    /** Answers each request with the next canned response and records what was sent. */
    private class FakeTransport(vararg responses: StudyLogRemote.Response) : StudyLogRemote.Transport {
        private val queue = ArrayDeque(responses.toList())
        val calls = mutableListOf<Call>()

        override fun send(method: String, url: String, token: String, body: String?): StudyLogRemote.Response {
            assertEquals("secret", token)
            calls += Call(method, url, body)
            return queue.removeFirst()
        }
    }

    private fun ok(sha: String) = StudyLogRemote.Response(200, """{"sha":"$sha","type":"file"}""", "4999")
    private fun written(status: Int, sha: String) =
        StudyLogRemote.Response(status, """{"content":{"sha":"$sha"},"commit":{"sha":"c1"}}""", "4999")

    private fun status(code: Int, remaining: String? = "4999") = StudyLogRemote.Response(code, "{}", remaining)

    private fun push(transport: StudyLogRemote.Transport, knownSha: String? = null) =
        StudyLogRemote.push("owner/notes", "area/cresto-study-log.md", " secret ", "# 记录\n", knownSha, transport)

    private fun bodyOf(call: Call): JsonObject = Json.parseToJsonElement(call.body!!) as JsonObject

    @Test
    fun requestBodyCarriesContentAndOptionalSha() {
        val created = Json.parseToJsonElement(StudyLogRemote.requestBody("学习\n", null)) as JsonObject
        assertEquals(StudyLogRemote.COMMIT_MESSAGE, created["message"]!!.jsonPrimitive.content)
        assertEquals(
            "学习\n",
            String(Base64.getDecoder().decode(created["content"]!!.jsonPrimitive.content), Charsets.UTF_8)
        )
        assertFalse(created.containsKey("sha"))

        val replaced = Json.parseToJsonElement(StudyLogRemote.requestBody("x", "abc")) as JsonObject
        assertEquals("abc", replaced["sha"]!!.jsonPrimitive.content)
    }

    @Test
    fun classifiesFailures() {
        assertEquals(StudyLogSyncError.Unauthorized, StudyLogRemote.classify(401, null))
        assertEquals(StudyLogSyncError.NoWriteAccess, StudyLogRemote.classify(403, "4999"))
        assertEquals(StudyLogSyncError.RateLimited, StudyLogRemote.classify(403, "0"))
        assertEquals(StudyLogSyncError.RateLimited, StudyLogRemote.classify(429, null))
        assertEquals(StudyLogSyncError.NotFound, StudyLogRemote.classify(404, null))
        assertEquals(StudyLogSyncError.Server, StudyLogRemote.classify(500, null))
        assertNull(StudyLogRemote.classify(409, null))
        assertNull(StudyLogRemote.classify(422, null))
    }

    @Test
    fun doesNotSendWithoutTokenOrValidSource() {
        val transport = FakeTransport()
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.NotConfigured),
            StudyLogRemote.push("owner/notes", "a.md", " ", "x", null, transport)
        )
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.InvalidSource),
            StudyLogRemote.push("owner", "a.md", "secret", "x", null, transport)
        )
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun createsTheFileWhenItDoesNotExist() {
        val transport = FakeTransport(status(404), written(201, "new"))

        assertEquals(StudyLogPushResult.Success("new"), push(transport))
        assertEquals(listOf("GET", "PUT"), transport.calls.map { it.method })
        assertTrue(transport.calls.all { it.url == url })
        assertFalse(bodyOf(transport.calls[1]).containsKey("sha"))
    }

    @Test
    fun replacesAnExistingFileFoundByLookup() {
        val transport = FakeTransport(ok("old"), written(200, "next"))

        assertEquals(StudyLogPushResult.Success("next"), push(transport))
        assertEquals("old", bodyOf(transport.calls[1])["sha"]!!.jsonPrimitive.content)
    }

    @Test
    fun usesTheKnownShaWithoutALookup() {
        val transport = FakeTransport(written(200, "next"))

        assertEquals(StudyLogPushResult.Success("next"), push(transport, knownSha = "last"))
        assertEquals(listOf("PUT"), transport.calls.map { it.method })
        assertEquals("last", bodyOf(transport.calls[0])["sha"]!!.jsonPrimitive.content)
    }

    @Test
    fun looksUpAgainOnceWhenTheFileChangedElsewhere() {
        val transport = FakeTransport(status(409), ok("edited"), written(200, "next"))

        assertEquals(StudyLogPushResult.Success("next"), push(transport, knownSha = "stale"))
        assertEquals(listOf("PUT", "GET", "PUT"), transport.calls.map { it.method })
        assertEquals("edited", bodyOf(transport.calls[2])["sha"]!!.jsonPrimitive.content)
    }

    @Test
    fun recreatesAFileRemovedElsewhere() {
        val transport = FakeTransport(status(422), status(404), written(201, "again"))

        assertEquals(StudyLogPushResult.Success("again"), push(transport, knownSha = "gone"))
        assertFalse(bodyOf(transport.calls[2]).containsKey("sha"))
    }

    @Test
    fun givesUpAfterASecondConflict() {
        val transport = FakeTransport(status(409), ok("a"), status(409))

        assertEquals(StudyLogPushResult.Failure(StudyLogSyncError.Server), push(transport, knownSha = "s"))
        assertEquals(3, transport.calls.size)
    }

    @Test
    fun reportsWhatTheUserMustFix() {
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.NoWriteAccess),
            push(FakeTransport(ok("old"), status(403)))
        )
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.Unauthorized),
            push(FakeTransport(status(401)))
        )
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.RateLimited),
            push(FakeTransport(status(403, remaining = "0")))
        )
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.InvalidSource),
            push(FakeTransport(StudyLogRemote.Response(200, """[{"name":"a.md"}]""", null)))
        )
        assertEquals(
            StudyLogPushResult.Failure(StudyLogSyncError.Server),
            push(FakeTransport(written(200, "")), knownSha = "s")
        )
    }

    @Test
    fun networkFailureIsReported() {
        val failing = StudyLogRemote.Transport { _, _, _, _ -> throw IOException("offline") }

        assertEquals(StudyLogPushResult.Failure(StudyLogSyncError.Network), push(failing))
    }
}
