package com.wonder.provider.ai

import com.wonder.provider.model.CuratedTour
import com.wonder.provider.model.TourBuildRequest

interface AiTourProvider {
    val sourceLabel: String
    suspend fun curateTour(request: TourBuildRequest): CuratedTour
}

data class AiTourOutcome(
    val tour: CuratedTour,
    val sourceLabel: String,
    val usedFallback: Boolean = false
)

sealed class AiTourError : Exception() {
    class ProviderFailed(message: String, cause: Throwable? = null) : AiTourError() {
        override val message: String = message
        init { cause?.let { initCause(it) } }
    }

    class InvalidApiKey(val provider: AiProviderType) : AiTourError() {
        override val message: String = "Invalid API key for ${provider.displayName}"
    }

    class NetworkError(cause: Throwable) : AiTourError() {
        override val message: String = "Network error — check your connection"
        init { initCause(cause) }
    }
}
