package com.localai.chat.core.error

sealed interface AppError {
    val userMessage: String

    data class Network(
        override val userMessage: String = "Cannot connect to the API endpoint.",
    ) : AppError

    data class Timeout(
        override val userMessage: String = "The API request timed out.",
    ) : AppError

    data class InvalidUrl(
        override val userMessage: String = "The API base URL is invalid.",
    ) : AppError

    data class Unauthorized(
        override val userMessage: String = "The API key was rejected or authorization failed.",
    ) : AppError

    data class ModelsUnsupported(
        override val userMessage: String = "This endpoint does not support /v1/models. You can enter a model manually.",
    ) : AppError

    data class Http(
        val code: Int,
        override val userMessage: String = "The API returned HTTP $code.",
    ) : AppError

    data class InvalidResponse(
        override val userMessage: String = "The API returned an unexpected response.",
    ) : AppError

    data class EmptyResponse(
        override val userMessage: String = "The API returned an empty response.",
    ) : AppError

    data class CleartextBlocked(
        override val userMessage: String = "Android blocked cleartext HTTP for this endpoint.",
    ) : AppError

    data class SecureStorage(
        override val userMessage: String = "The API key could not be read or saved securely.",
    ) : AppError

    data class Unknown(
        override val userMessage: String = "Something went wrong.",
    ) : AppError
}
