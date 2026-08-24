package com.machinecharades.net

import com.machinecharades.config.BuildConfig
import com.machinecharades.core.DailyPuzzle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The Worker's reply to a guess. */
@Serializable
data class GuessResponse(
    @SerialName("guess") val guess: String,
    @SerialName("correct") val correct: Boolean,
    @SerialName("confidence") val confidence: Float = 0.5f,
    @SerialName("cached") val cached: Boolean = false,
)

@Serializable
private data class GuessRequest(
    @SerialName("puzzleNumber") val puzzleNumber: Int,
    @SerialName("clue") val clue: String,
    @SerialName("attempt") val attempt: Int,
)

/** A failure worth showing the player, rather than a stack trace. */
sealed class ApiError(val display: String) {
    data object NotConfigured : ApiError("App is not configured. See local.properties.")
    data object NoPuzzleToday : ApiError("No puzzle scheduled for today yet.")
    data object Unauthenticated : ApiError("Could not sign in. Check your connection.")
    data object RateLimited : ApiError("That's enough for today. Come back tomorrow.")
    data object ModelUnavailable : ApiError("The machine is thinking too hard. Try again.")
    data class Unexpected(val detail: String) : ApiError("Something went wrong: $detail")
}

class ApiException(val error: ApiError) : Exception(error.display)

/**
 * Talks to the Cloudflare Worker.
 *
 * Every call carries a Firebase ID token; the Worker verifies the signature and
 * derives the player id from it. A 401 is retried exactly once with a fresh
 * token, because ID tokens last an hour and a long session will outlive one.
 */
class GameApi(
    private val http: HttpClient = defaultClient(),
    private val auth: FirebaseAuth = FirebaseAuth(http),
    private val baseUrl: String = BuildConfig.WORKER_URL.trimEnd('/'),
) {

    /**
     * Today's puzzle.
     *
     * `dev.puzzleNumber` in local.properties forces a specific puzzle. The real
     * schedule starts on launch day, so without it there is nothing to show
     * before then. It only works against a Worker running with
     * ALLOW_UNVERIFIED, which no deployed Worker does.
     */
    suspend fun today(): DailyPuzzle {
        requireConfigured()
        val dev = BuildConfig.DEV_PUZZLE_NUMBER
        val suffix = if (dev.isNotEmpty()) "?n=$dev" else ""
        return authorized { token ->
            http.get("$baseUrl/puzzle/today$suffix") {
                header("authorization", "Bearer $token")
            }
        }.body()
    }

    suspend fun guess(puzzleNumber: Int, clue: String, attempt: Int): GuessResponse {
        requireConfigured()
        return authorized { token ->
            http.post("$baseUrl/guess") {
                header("authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(GuessRequest(puzzleNumber, clue, attempt))
            }
        }.body()
    }

    private fun requireConfigured() {
        if (baseUrl.isEmpty()) throw ApiException(ApiError.NotConfigured)
    }

    /** Runs [call], refreshing the token once on 401, then maps status to error. */
    private suspend fun authorized(call: suspend (String) -> HttpResponse): HttpResponse {
        var res = call(auth.idToken())
        if (res.status == HttpStatusCode.Unauthorized) {
            auth.invalidate()
            res = call(auth.idToken())
        }
        if (res.status.isSuccess()) return res
        throw ApiException(
            when (res.status) {
                HttpStatusCode.Unauthorized -> ApiError.Unauthenticated
                HttpStatusCode.TooManyRequests -> ApiError.RateLimited
                HttpStatusCode.ServiceUnavailable -> ApiError.ModelUnavailable
                HttpStatusCode.NotFound -> ApiError.NoPuzzleToday
                else -> ApiError.Unexpected("HTTP ${res.status.value}")
            },
        )
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299

    companion object {
        fun defaultClient(): HttpClient = platformHttpClient {
            install(ContentNegotiation) {
                // The Worker adds fields before a shipped binary updates, so an
                // unknown key must not break decoding.
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
