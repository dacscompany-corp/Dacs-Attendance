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
    val submitting: Boolean = false,
    val failure: LoginFailure? = null,
    /** Set once, when sign-in succeeded; the root reads it and moves on. */
    val signedIn: WorkerProfile? = null
) {
    val canSubmit: Boolean get() = !submitting
}

/**
 * The login screen's state machine.
 *
 * Every refusal reaching [LoginUiState.failure] was decided server-side
 * by the sign-in function, so this class never has to guess why a worker
 * was turned away.
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
        if (state.submitting) return

        // Trimmed because a trailing space from a phone keyboard is an
        // authentication failure the worker cannot see.
        if (state.email.trim().isEmpty() || state.password.isEmpty()) {
            _uiState.update { it.copy(failure = LoginFailure.MissingFields) }
            return
        }

        _uiState.update { it.copy(submitting = true, failure = null) }
        viewModelScope.launch {
            val result = auth.signIn(state.email.trim(), state.password)
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
}
