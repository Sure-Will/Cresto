package com.nevoit.cresto.feature.settings.util

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nevoit.cresto.data.ai.AiRequestException
import com.nevoit.cresto.data.ai.AiTodoExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface AIConnectionTestState {
    data object Idle : AIConnectionTestState
    data object Testing : AIConnectionTestState
    data class Success(val latencyMillis: Long) : AIConnectionTestState
    data class Failure(val reason: AIConnectionTestFailure) : AIConnectionTestState
}

enum class AIConnectionTestFailure {
    MissingApiKey,
    InvalidUrl,
    Authentication,
    EndpointOrModel,
    RateLimited,
    Timeout,
    Network,
    IncompatibleResponse,
    ProviderUnavailable,
    Unknown
}

internal fun classifyAIConnectionTestFailure(error: Throwable): AIConnectionTestFailure {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()
    val requestError = causes.filterIsInstance<AiRequestException>().firstOrNull()

    return when {
        requestError is AiRequestException.MissingApiKey -> {
            AIConnectionTestFailure.MissingApiKey
        }

        causes.any { it is MalformedURLException } -> AIConnectionTestFailure.InvalidUrl
        requestError is AiRequestException.HttpStatus &&
                requestError.statusCode in setOf(401, 403) -> {
            AIConnectionTestFailure.Authentication
        }

        requestError is AiRequestException.HttpStatus &&
                requestError.statusCode in setOf(400, 404) -> {
            AIConnectionTestFailure.EndpointOrModel
        }

        requestError is AiRequestException.HttpStatus && requestError.statusCode == 429 -> {
            AIConnectionTestFailure.RateLimited
        }

        requestError is AiRequestException.HttpStatus &&
                requestError.statusCode in 500..599 -> {
            AIConnectionTestFailure.ProviderUnavailable
        }

        causes.any { it is SocketTimeoutException } -> AIConnectionTestFailure.Timeout
        causes.any { it is UnknownHostException } -> AIConnectionTestFailure.Network
        causes.any { it is SerializationException } ||
                requestError is AiRequestException.IncompatibleResponse ||
                requestError is AiRequestException.ResponseTooLarge -> {
            AIConnectionTestFailure.IncompatibleResponse
        }

        causes.any { it is IOException } -> AIConnectionTestFailure.Network
        else -> AIConnectionTestFailure.Unknown
    }
}

class AISettingsViewModel(
    private val aiTodoExtractor: AiTodoExtractor = AiTodoExtractor()
) : ViewModel() {

    val apiUrl = mutableStateOf(SettingsManager.aiApiUrl)
    val apiKey = mutableStateOf(SettingsManager.aiApiKey)
    val textModel = mutableStateOf(SettingsManager.aiTextModel)
    val multimodalModel = mutableStateOf(SettingsManager.aiMultimodalModel)
    val lastSavedAt = mutableLongStateOf(0L)
    val connectionTestState = mutableStateOf<AIConnectionTestState>(AIConnectionTestState.Idle)

    private var connectionTestJob: Job? = null
    private val connectionTestMutex = Mutex()

    fun onApiUrlChanged(value: String) {
        invalidateConnectionTest()
        apiUrl.value = value
        saveSettingsIfChanged()
    }

    fun onApiKeyChanged(value: String) {
        invalidateConnectionTest()
        apiKey.value = value
        saveSettingsIfChanged()
    }

    fun onTextModelChanged(value: String) {
        invalidateConnectionTest()
        textModel.value = value
        saveSettingsIfChanged()
    }

    fun onMultimodalModelChanged(value: String) {
        invalidateConnectionTest()
        multimodalModel.value = value
        saveSettingsIfChanged()
    }

    fun restoreDefaults() {
        SettingsManager.resetAiSettingsToDefaults()
        apiUrl.value = SettingsManager.aiApiUrl
        apiKey.value = SettingsManager.aiApiKey
        textModel.value = SettingsManager.aiTextModel
        multimodalModel.value = SettingsManager.aiMultimodalModel
        lastSavedAt.longValue = System.currentTimeMillis()
        invalidateConnectionTest()
    }

    fun testConnection() {
        if (connectionTestState.value is AIConnectionTestState.Testing) return

        val currentApiUrl = apiUrl.value
        val currentApiKey = apiKey.value
        val currentTextModel = textModel.value

        connectionTestState.value = AIConnectionTestState.Testing
        connectionTestJob = viewModelScope.launch {
            try {
                val result = connectionTestMutex.withLock {
                    aiTodoExtractor.testConnection(
                        apiUrl = currentApiUrl,
                        apiKey = currentApiKey,
                        model = currentTextModel
                    )
                }
                connectionTestState.value = AIConnectionTestState.Success(
                    latencyMillis = result.latencyMillis
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                connectionTestState.value = AIConnectionTestState.Failure(
                    reason = classifyAIConnectionTestFailure(error)
                )
            }
        }
    }

    fun saveSettings() {
        val newApiUrl = apiUrl.value.trim()
        val newApiKey = apiKey.value.trim()
        val newTextModel = textModel.value.trim()
        val newMultimodalModel = multimodalModel.value.trim()

        SettingsManager.aiApiUrl = newApiUrl
        SettingsManager.aiApiKey = newApiKey
        SettingsManager.aiTextModel = newTextModel
        SettingsManager.aiMultimodalModel = newMultimodalModel
        lastSavedAt.longValue = System.currentTimeMillis()
    }

    private fun saveSettingsIfChanged() {
        val newApiUrl = apiUrl.value.trim()
        val newApiKey = apiKey.value.trim()
        val newTextModel = textModel.value.trim()
        val newMultimodalModel = multimodalModel.value.trim()

        if (
            newApiUrl == SettingsManager.aiApiUrl &&
            newApiKey == SettingsManager.aiApiKey &&
            newTextModel == SettingsManager.aiTextModel &&
            newMultimodalModel == SettingsManager.aiMultimodalModel
        ) {
            return
        }

        saveSettings()
    }

    private fun invalidateConnectionTest() {
        connectionTestJob?.cancel()
        connectionTestJob = null
        connectionTestState.value = AIConnectionTestState.Idle
    }
}
