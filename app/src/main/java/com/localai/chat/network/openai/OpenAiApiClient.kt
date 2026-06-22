package com.localai.chat.network.openai

import com.localai.chat.core.error.AppError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OpenAiApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val streamParser: ChatCompletionStreamParser,
) {
    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String?,
        request: ChatCompletionRequestDto,
    ): Flow<ApiResult<ChatStreamEvent>> = flow {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val httpUrl = normalizedBaseUrl.toHttpUrlOrNull()
        if (httpUrl == null) {
            emit(ApiResult.Failure(AppError.InvalidUrl()))
            return@flow
        }

        val requestJson = json.encodeToString(ChatCompletionRequestDto.serializer(), request.copy(stream = true))
        val requestBuilder = Request.Builder()
            .url(httpUrl.newBuilder().addPathSegment("chat").addPathSegment("completions").build())
            .post(requestJson.toRequestBody(JsonMediaType))

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        val call = okHttpClient.newCall(requestBuilder.build())
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }

        val response = withContext(Dispatchers.IO) { call.execute() }
        response.use {
            when (it.code) {
                401, 403 -> {
                    emit(ApiResult.Failure(AppError.Unauthorized()))
                    return@use
                }
                404 -> {
                    emit(ApiResult.Failure(AppError.Http(it.code, "/v1/chat/completions was not found.")))
                    return@use
                }
                !in 200..299 -> {
                    emit(ApiResult.Failure(AppError.Http(it.code)))
                    return@use
                }
            }

            val source = it.body?.source()
            if (source == null) {
                emit(ApiResult.Failure(AppError.EmptyResponse()))
                return@use
            }

            val eventData = mutableListOf<String>()

            suspend fun flushEvent() {
                if (eventData.isEmpty()) return
                val data = eventData.joinToString(separator = "\n")
                eventData.clear()
                val event = streamParser.parseData(data)
                if (event != ChatStreamEvent.Empty) emit(ApiResult.Success(event))
            }

            while (true) {
                val line = withContext(Dispatchers.IO) { source.readUtf8Line() } ?: break
                when {
                    line.isBlank() -> flushEvent()
                    line.startsWith(":") -> Unit
                    line.startsWith("data:") -> eventData += line.removePrefix("data:").trimStart()
                }
            }
            flushEvent()
        }
    }.catch { exception ->
        if (exception is CancellationException) throw exception
        emit(ApiResult.Failure(mapException(exception)))
    }

    suspend fun createChatCompletion(
        baseUrl: String,
        apiKey: String?,
        request: ChatCompletionRequestDto,
    ): ApiResult<ChatCompletionResult> = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val httpUrl = normalizedBaseUrl.toHttpUrlOrNull()
            ?: return@withContext ApiResult.Failure(AppError.InvalidUrl())

        val requestJson = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
        val requestBuilder = Request.Builder()
            .url(httpUrl.newBuilder().addPathSegment("chat").addPathSegment("completions").build())
            .post(requestJson.toRequestBody(JsonMediaType))

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    401, 403 -> ApiResult.Failure(AppError.Unauthorized())
                    404 -> ApiResult.Failure(AppError.Http(response.code, "/v1/chat/completions was not found."))
                    in 200..299 -> {
                        val body = response.body?.string()
                            ?: return@use ApiResult.Failure(AppError.EmptyResponse())
                        if (body.isBlank()) return@use ApiResult.Failure(AppError.EmptyResponse())

                        val decoded = json.decodeFromString<ChatCompletionResponseDto>(body)
                        val choice = decoded.choices.firstOrNull()
                            ?: return@use ApiResult.Failure(AppError.EmptyResponse())
                        val content = choice.message?.content?.takeIf(String::isNotEmpty)
                            ?: choice.text?.takeIf(String::isNotEmpty)
                            ?: ""
                        val reasoning = choice.message?.reasoningContent?.takeIf(String::isNotEmpty)
                            ?: choice.message?.reasoning?.takeIf(String::isNotEmpty)

                        if (content.isBlank() && reasoning.isNullOrBlank()) {
                            return@use ApiResult.Failure(AppError.EmptyResponse())
                        }

                        ApiResult.Success(
                            ChatCompletionResult(
                                content = content,
                                rawContent = buildRawAssistantContent(reasoning, content),
                                usage = decoded.usage,
                            ),
                        )
                    }
                    else -> ApiResult.Failure(AppError.Http(response.code))
                }
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            ApiResult.Failure(mapException(exception))
        }
    }

    suspend fun listModels(
        baseUrl: String,
        apiKey: String?,
    ): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val httpUrl = normalizedBaseUrl.toHttpUrlOrNull()
            ?: return@withContext ApiResult.Failure(AppError.InvalidUrl())

        val requestBuilder = Request.Builder()
            .url(httpUrl.newBuilder().addPathSegment("models").build())
            .get()

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    401, 403 -> ApiResult.Failure(AppError.Unauthorized())
                    404, 405 -> ApiResult.Failure(AppError.ModelsUnsupported())
                    in 200..299 -> {
                        val body = response.body?.string()
                            ?: return@use ApiResult.Failure(AppError.EmptyResponse())
                        if (body.isBlank()) return@use ApiResult.Failure(AppError.EmptyResponse())

                        val models = json.decodeFromString<ModelsResponseDto>(body)
                            .data
                            .mapNotNull { it.id.trim().takeIf(String::isNotEmpty) }
                            .distinct()

                        ApiResult.Success(models)
                    }
                    else -> ApiResult.Failure(AppError.Http(response.code))
                }
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            ApiResult.Failure(mapException(exception))
        }
    }

    fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    private fun mapException(exception: Throwable): AppError {
        return when (exception) {
            is SocketTimeoutException -> AppError.Timeout()
            is UnknownHostException -> AppError.Network()
            is ConnectException -> AppError.Network()
            is SerializationException -> AppError.InvalidResponse()
            is IllegalArgumentException -> AppError.InvalidUrl()
            is IOException -> {
                if (exception.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                    AppError.CleartextBlocked()
                } else {
                    AppError.Network()
                }
            }
            else -> AppError.Unknown()
        }
    }

    private fun buildRawAssistantContent(
        reasoning: String?,
        content: String,
    ): String {
        val trimmedReasoning = reasoning?.trim().orEmpty()
        if (trimmedReasoning.isBlank()) return content

        return buildString {
            append("<think>")
            append(trimmedReasoning)
            append("</think>")
            if (content.isNotBlank()) {
                append("\n\n")
                append(content.trimStart())
            }
        }
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: AppError) : ApiResult<Nothing>
}
