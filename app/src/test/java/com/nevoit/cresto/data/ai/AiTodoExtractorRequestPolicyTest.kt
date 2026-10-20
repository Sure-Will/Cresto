package com.nevoit.cresto.data.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AiTodoExtractorRequestPolicyTest {

    @Test
    fun imageRequest_usesDeepSeekVisionPolicy() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://api.deepseek.com/v1/chat/completions"))
        val extractor = extractorFor(
            apiUrl = "https://api.deepseek.com",
            connection = connection
        )

        val result = extractor.extractFromImage(PRIVATE_IMAGE_DATA_URL)

        val body = Json.parseToJsonElement(connection.requestBody()).jsonObject
        assertEquals("提交报告", result.items.single().title)
        val imagePart = body.getValue("messages").jsonArray[1].jsonObject
            .getValue("content").jsonArray[1].jsonObject
        assertEquals("image_url", imagePart.getValue("type").jsonPrimitive.content)
        assertTrue(
            imagePart.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content ==
                    PRIVATE_IMAGE_DATA_URL
        )
        assertEquals(90_000, connection.readTimeout)
        assertFalse(body.containsKey("max_tokens"))
        assertEquals(
            "json_object",
            body.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content
        )
        assertEquals(
            "disabled",
            body.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content
        )
    }

    @Test
    fun imageRequest_keepsCustomProviderOpenAiCompatible() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://compatible.example/v1/chat/completions"))
        val extractor = extractorFor(
            apiUrl = "https://compatible.example",
            connection = connection
        )

        extractor.extractFromImage(PRIVATE_IMAGE_DATA_URL)

        val body = Json.parseToJsonElement(connection.requestBody()).jsonObject
        assertEquals(90_000, connection.readTimeout)
        assertFalse(body.containsKey("max_tokens"))
        assertFalse(body.containsKey("response_format"))
        assertFalse(body.containsKey("thinking"))
    }

    @Test
    fun textRequest_keepsExistingTimeoutAndRequestShape() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://api.deepseek.com/v1/chat/completions"))
        val extractor = extractorFor(
            apiUrl = "https://api.deepseek.com",
            connection = connection
        )

        extractor.extractFromText("明天提交报告")

        val body = Json.parseToJsonElement(connection.requestBody()).jsonObject
        assertEquals(30_000, connection.readTimeout)
        assertFalse(body.containsKey("max_tokens"))
        assertFalse(body.containsKey("response_format"))
        assertFalse(body.containsKey("thinking"))
    }

    @Test
    fun imageTimeout_identifiesResponseStageWithoutLeakingSecrets() {
        val connection = FakeHttpURLConnection(
            url = URL("https://api.deepseek.com/v1/chat/completions"),
            responseCodeFailure = SocketTimeoutException("$PRIVATE_API_KEY $PRIVATE_IMAGE_DATA_URL")
        )
        val extractor = extractorFor(
            apiUrl = "https://api.deepseek.com",
            connection = connection
        )

        val error = assertThrows(Throwable::class.java) {
            runBlocking { extractor.extractFromImage(PRIVATE_IMAGE_DATA_URL) }
        }

        assertTrue(error is AiRequestException.Timeout)
        val timeout = error as AiRequestException.Timeout
        assertEquals(AiRequestException.Timeout.Stage.WaitingForResponse, timeout.stage)
        assertEquals("等待模型响应超时，请稍后重试", timeout.message)
        assertFalse(error.message.orEmpty().contains(PRIVATE_API_KEY))
        assertFalse(error.message.orEmpty().contains(PRIVATE_IMAGE_DATA_URL))
        assertFalse(error.cause?.message.orEmpty().contains(PRIVATE_API_KEY))
        assertFalse(error.cause?.message.orEmpty().contains(PRIVATE_IMAGE_DATA_URL))
    }

    private fun extractorFor(
        apiUrl: String,
        connection: FakeHttpURLConnection
    ): AiTodoExtractor {
        return AiTodoExtractor(
            configurationProvider = AiConfigurationProvider {
                AiConfiguration(
                    apiUrl = apiUrl,
                    apiKey = PRIVATE_API_KEY,
                    textModel = "test-text-model",
                    multimodalModel = "test-vision-model"
                )
            },
            connectionFactory = { connection }
        )
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseCodeFailure: SocketTimeoutException? = null
    ) : HttpURLConnection(url) {
        private val requestBytes = ByteArrayOutputStream()

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): ByteArrayOutputStream = requestBytes

        override fun getResponseCode(): Int {
            responseCodeFailure?.let { throw it }
            return HTTP_OK
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(SUCCESS_RESPONSE.toByteArray())

        fun requestBody(): String = requestBytes.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val PRIVATE_API_KEY = "secret-test-api-key"
        const val PRIVATE_IMAGE_DATA_URL = "data:image/jpeg;base64,private-image-payload"
        const val SUCCESS_RESPONSE =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"quantity\\\":1,\\\"items\\\":[{\\\"title\\\":\\\"提交报告\\\",\\\"date\\\":\\\"2026-09-05\\\"}]}\"}}]}"
    }
}
