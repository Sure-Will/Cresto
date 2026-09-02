package com.nevoit.cresto.feature.settings.util

import com.nevoit.cresto.data.ai.AiRequestException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AIConnectionTestFailureTest {

    @Test
    fun classify_missingApiKey() {
        assertEquals(
            AIConnectionTestFailure.MissingApiKey,
            classifyAIConnectionTestFailure(AiRequestException.MissingApiKey)
        )
    }

    @Test
    fun classify_httpStatusCodes() {
        assertEquals(
            AIConnectionTestFailure.Authentication,
            classifyAIConnectionTestFailure(AiRequestException.HttpStatus(401))
        )
        assertEquals(
            AIConnectionTestFailure.EndpointOrModel,
            classifyAIConnectionTestFailure(AiRequestException.HttpStatus(404))
        )
        assertEquals(
            AIConnectionTestFailure.RateLimited,
            classifyAIConnectionTestFailure(AiRequestException.HttpStatus(429))
        )
        assertEquals(
            AIConnectionTestFailure.ProviderUnavailable,
            classifyAIConnectionTestFailure(AiRequestException.HttpStatus(503))
        )
    }

    @Test
    fun classify_networkFailures() {
        assertEquals(
            AIConnectionTestFailure.Timeout,
            classifyAIConnectionTestFailure(SocketTimeoutException("timeout"))
        )
        assertEquals(
            AIConnectionTestFailure.Network,
            classifyAIConnectionTestFailure(UnknownHostException("host"))
        )
    }

    @Test
    fun classify_incompatibleResponse() {
        assertEquals(
            AIConnectionTestFailure.IncompatibleResponse,
            classifyAIConnectionTestFailure(SerializationException("invalid response"))
        )
        assertEquals(
            AIConnectionTestFailure.IncompatibleResponse,
            classifyAIConnectionTestFailure(AiRequestException.IncompatibleResponse)
        )
        assertEquals(
            AIConnectionTestFailure.IncompatibleResponse,
            classifyAIConnectionTestFailure(AiRequestException.ResponseTooLarge)
        )
    }
}
