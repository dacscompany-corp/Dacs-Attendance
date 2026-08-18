package com.dacs.attendance.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.LoginRejected
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    /** The Turnstile challenge is on screen and we are waiting for a token. */
    val awaitingCaptcha: Boolean = false,
    val submitting: Boolean = false,
    val failure: LoginFailure? = null,
    /** Set once, when sign-in succeeded; the root reads it and moves on. */
    val signedIn: WorkerProfile? = null
) {
    val canSubmit: Boolean get() = !submitting && !awaitingCaptcha
    val busy: Boolean get() = submitting || awaitingCaptcha
}

/**
 * Sign-in is two steps, not one: solve the captcha, then authenticate.
 *
 * The project enforces Cloudflare Turnstile on auth, so a password grant
 * with no token is refused before it is ever checked. Asking for the
 * token first means a wrong password and a missing token stay distinct
 * failures instead of arriving as the same opaque one.
 *
 * The challenge itself is a WebView the screen owns; this class only
 * knows that a token either arrived or did not.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Typing clears the error. Leaving a red box up while the worker is
    // fixing the very field it complains about is just noise.
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, failure = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, failure = null) }

    fun onTogglePasswordVisible() =
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val state = _uiState.value
        if (state.busy) return

        // Trimmed because a trailing space from a phone keyboard is an
        // authentication failure the worker cannot see.
        if (state.email.trim().isEmpty() || state.password.isEmpty()) {
            _uiState.update { it.copy(failure = LoginFailure.MissingFields) }
            return
        }

        _uiState.update { it.copy(awaitingCaptcha = true, failure = null) }
    }

    fun onCaptchaToken(token: String) {
        val state = _uiState.value
        if (!state.awaitingCaptcha) return

        _uiState.update { it.copy(awaitingCaptcha = false, submitting = true) }
        viewModelScope.launch {
            val result = auth.signIn(state.email.trim(), state.password, token)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { worker ->
                        current.copy(submitting = false, failure = null, signedIn = worker)
                    },
                    onFailure = { error ->
                        current.copy(
                            submitting = false,
                            failure = (error as? LoginRejected)?.failure ?: LoginFailure.of(error)
                        )
                    }
                )
            }
        }
    }

    /** The challenge failed, expired, or the worker backed out of it. */
    fun onCaptchaFailed() = _uiState.update {
        it.copy(
            awaitingCaptcha = false,
            submitting = false,
            failure = LoginFailure.CaptchaRequired
        )
    }
}
