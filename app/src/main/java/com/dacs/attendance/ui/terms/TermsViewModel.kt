package com.dacs.attendance.ui.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.repo.TermsRepository
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TermsUiState(
    val agreed: Boolean = false,
    val submitting: Boolean = false,
    val failure: LoginFailure? = null,
    val accepted: Boolean = false
)

@HiltViewModel
class TermsViewModel @Inject constructor(
    private val terms: TermsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TermsUiState())
    val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    fun onAgreedChange(agreed: Boolean) =
        _uiState.update { it.copy(agreed = agreed, failure = null) }

    /**
     * Acceptance REQUIRES a connection. It writes the audit evidence, and
     * a locally queued "I accepted" that never reaches the server is worse
     * than making the worker wait for signal once, on their first day.
     * This is the only screen in the app allowed to demand the network.
     */
    fun onAccept(worker: WorkerProfile) {
        val state = _uiState.value
        if (!state.agreed || state.submitting) return

        _uiState.update { it.copy(submitting = true, failure = null) }
        viewModelScope.launch {
            val result = terms.accept(worker)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(submitting = false, accepted = true) },
                    onFailure = { error ->
                        current.copy(submitting = false, failure = LoginFailure.of(error))
                    }
                )
            }
        }
    }
}
