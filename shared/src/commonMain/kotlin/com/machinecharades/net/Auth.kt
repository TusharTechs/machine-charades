package com.machinecharades.net

import com.machinecharades.config.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anonymous sign-in against Firebase Auth, over its REST API.
 *
 * Deliberately not the Firebase SDK. The KMP wrapper needs the native Firebase
 * framework linked by hand in Xcode and a google-services.json on Android, which
 * is a lot of setup to obtain one string. The REST endpoint is the same Firebase
 * Auth, returns a real ID token the Worker verifies against Google's JWKS, and
 * needs nothing but an HTTP client.
 *
 * What this does NOT do, and will need before launch:
 *   - persist the refresh token, so a returning player keeps their identity.
 *     Right now every cold start is a new anonymous user, which means a new
 *     player id and a reset rate-limit bucket.
 *   - use the refresh endpoint rather than re-signing-up.
 * Both matter for real play; neither blocks seeing a puzzle.
 */
class FirebaseAuth(private val http: HttpClient) {

    @Serializable
    private data class SignUpRequest(val returnSecureToken: Boolean = true)

    @Serializable
    private data class SignUpResponse(
        @SerialName("idToken") val idToken: String,
        @SerialName("refreshToken") val refreshToken: String = "",
        /** Seconds, as a string — Google sends it that way. */
        @SerialName("expiresIn") val expiresIn: String = "3600",
        @SerialName("localId") val localId: String = "",
    )

    private var cached: String? = null

    /** An ID token, signing in on first use. */
    suspend fun idToken(): String {
        cached?.let { return it }
        if (BuildConfig.FIREBASE_WEB_API_KEY.isEmpty()) {
            error("firebase.webApiKey missing from local.properties")
        }
        val res: SignUpResponse = http.post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp" +
                "?key=${BuildConfig.FIREBASE_WEB_API_KEY}",
        ) {
            contentType(ContentType.Application.Json)
            setBody(SignUpRequest())
        }.body()
        return res.idToken.also { cached = it }
    }

    /** Drops the cached token, so the next call signs in again. */
    fun invalidate() { cached = null }
}
