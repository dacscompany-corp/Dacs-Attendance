package com.dacs.attendance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.local.TermsCache
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.TermsRepository
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.Eligibility
import com.dacs.attendance.domain.StartupGate
import com.dacs.attendance.domain.WorkerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the app is: signed out, at the Terms, or in. The screens below
 * only ever move it forward.
 */
sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class NeedsTerms(val worker: WorkerProfile) : AppState
    data class SignedIn(val worker: WorkerProfile) : AppState

    /** Signed in, but offline and this device has never seen an acceptance. */
    data class GateUnavailable(val worker: WorkerProfile) : AppState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val terms: TermsRepository,
    private val termsCache: TermsCache
) : ViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        // The session is restored from encrypted storage, so this is the
        // usual path every morning -- not just after a fresh install.
        viewModelScope.launch { resume() }
    }

    private suspend fun resume() {
        val worker = auth.currentWorker()
        when {
            worker == null -> _state.value = AppState.SignedOut

            // The session outlives the account. An admin who turns a
            // worker off expects them out of the app, not carried in by a
            // token issued last week -- the RPCs would refuse them anyway,
            // four screens later, with no explanation.
            worker.eligibility() != Eligibility.Allowed -> {
                auth.signOut()
                _state.value = AppState.SignedOut
            }

            else -> _state.value = evaluate(worker)
        }
    }

    fun onSignedIn(worker: WorkerProfile) {
        viewModelScope.launch { _state.value = evaluate(worker) }
    }

    fun onTermsAccepted(worker: WorkerProfile) {
        termsCache.remember(worker.id, AttendanceTerms.VERSION)
        _state.value = AppState.SignedIn(worker)
    }

    fun onRetryGate(worker: WorkerProfile) {
        _state.value = AppState.Loading
        viewModelScope.launch { _state.value = evaluate(worker) }
    }

    fun onSignOut() {
        _state.value = AppState.Loading
        viewModelScope.launch {
            auth.signOut()
            _state.value = AppState.SignedOut
        }
    }

    private suspend fun evaluate(worker: WorkerProfile): AppState {
        val accepted = terms.acceptedVersions(worker.id)
        val decision = StartupGate.decide(
            acceptedVersions = accepted,
            cachedVersion = termsCache.acceptedVersion(worker.id),
            currentVersion = AttendanceTerms.VERSION
        )

        // Only refresh the cache from an answer the SERVER gave.
        if (accepted.isSuccess && decision == StartupGate.Decision.Ready) {
            termsCache.remember(worker.id, AttendanceTerms.VERSION)
        }

        return when (decision) {
            StartupGate.Decision.Ready -> AppState.SignedIn(worker)
            StartupGate.Decision.MustAccept -> AppState.NeedsTerms(worker)
            StartupGate.Decision.Unavailable -> AppState.GateUnavailable(worker)
        }
    }
}
