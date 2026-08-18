package com.dacs.attendance.data.remote

import com.dacs.attendance.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sign-in, done server-side by the `attendance-signin` Edge Function.
 *
 * The app does NOT call the auth endpoint directly, and the reason is
 * worth stating: this Supabase project enforces Cloudflare Turnstile on
 * auth, Turnstile has no native Android SDK, and the only client-side way
 * to solve it is a WebView loading a web page. Calls made with the
 * service_role key skip the captcha check, so the function does the
 * sign-in and this stays one ordinary HTTPS request from Kotlin.
 *
 * The function also decides eligibility, so a deactivated or non-worker
 * account never receives tokens at all.
 */
@Singleton
class SignInApi @Inject constructor(
    private val http: HttpClient
) {
    suspend fun signIn(email: String, password: String): SignInOutcome {
        val response = http.post(BuildConfig.SIGN_IN_FUNCTION_URL) {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            contentType(ContentType.Application.Json)
            setBody(SignInRequest(email = email, password = password))
        }

        return if (response.status.isSuccess()) {
            SignInOutcome.Success(response.body())
        } else {
            // A body we cannot parse is still a refusal -- report it as an
            // unknown code rather than letting a parse error surface as a
            // network problem.
            val code = runCatching { response.body<SignInErrorBody>().error }.getOrNull()
            SignInOutcome.Refused(code)
        }
    }
}

sealed interface SignInOutcome {
    data class Success(val body: SignInResponse) : SignInOutcome
    data class Refused(val code: String?) : SignInOutcome
}

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class SignInResponse(
    val session: SessionDto,
    val worker: ProfileRow
)

@Serializable
data class SessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String = "bearer"
)

@Serializable
data class SignInErrorBody(val error: String? = null)
