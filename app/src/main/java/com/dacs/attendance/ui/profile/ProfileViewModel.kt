package com.dacs.attendance.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.local.TermsCache
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.TermsRepository
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.PasswordChangeFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    /** Null until known, and left null when it cannot be. */
    val acceptedAt: Instant? = null,
    val changingPassword: Boolean = false,
    val passwordFailure: PasswordChangeFailure? = null,
    /** Set once, so the dialog can confirm before it closes itself. */
    val passwordChanged: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val terms: TermsRepository,
    private val termsCache: TermsCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * Cache first, then the server. On site the cache is usually the only
     * answer available, and a subtitle that appears a second late is
     * better than a row that never says when the worker agreed.
     */
    fun load(workerId: String) {
        termsCache.acceptedAt(workerId)?.let { cached ->
            _uiState.update { it.copy(acceptedAt = cached) }
        }

        viewModelScope.launch {
            terms.acceptedAt(workerId, AttendanceTerms.VERSION)
                .getOrNull()
                ?.let { fresh ->
                    termsCache.rememberAcceptedAt(workerId, fresh)
                    _uiState.update { it.copy(acceptedAt = fresh) }
                }
        }
    }

    fun changePassword(password: String, confirmation: String) {
        // Refuse locally what the server would refuse anyway, and what it
        // could never see: it only ever receives one of the two boxes.
        PasswordChangeFailure.validate(password, confirmation)?.let { problem ->
            _uiState.update { it.copy(passwordFailure = problem) }
            return
        }

        _uiState.update { it.copy(changingPassword = true, passwordFailure = null) }

        viewModelScope.launch {
            auth.changePassword(password).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(changingPassword = false, passwordChanged = true)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            changingPassword = false,
                            passwordFailure = PasswordChangeFailure.of(error)
                        )
                    }
                }
            )
        }
    }

    /** Clears the dialog's result when it is dismissed or reopened. */
    fun resetPasswordChange() {
        _uiState.update {
            it.copy(changingPassword = false, passwordFailure = null, passwordChanged = false)
        }
    }
}
