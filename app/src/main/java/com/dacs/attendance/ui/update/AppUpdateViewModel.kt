package com.dacs.attendance.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.local.InstallGateway
import com.dacs.attendance.data.repo.AppUpdateRepository
import com.dacs.attendance.data.repo.DownloadResult
import com.dacs.attendance.domain.AppRelease
import com.dacs.attendance.domain.UpdateFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The required-update dialog, as a state machine.
 *
 * ```
 * Hidden ──newer release known──► Available
 * Available ──UPDATE NOW──► NeedsPermission ──switch on──► Downloading
 *                     └───────────────(already allowed)──► Downloading
 * Downloading ──verified──► ReadyToInstall ──Install──► Android installer
 * Downloading ──no signal / damaged──► Failed ──Try again──► Downloading
 * ```
 *
 * There is no way back to Hidden except this build becoming current,
 * which in practice means the new APK replacing it.
 */
sealed interface UpdateState {
    data object Hidden : UpdateState

    sealed interface Shown : UpdateState {
        val release: AppRelease
    }

    data class Available(override val release: AppRelease) : Shown
    data class NeedsPermission(override val release: AppRelease) : Shown
    data class Downloading(override val release: AppRelease, val progress: Float) : Shown
    data class ReadyToInstall(override val release: AppRelease, val apk: File) : Shown
    data class Failed(override val release: AppRelease, val reason: UpdateFailure) : Shown
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updates: AppUpdateRepository,
    private val installer: InstallGateway
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checking: Job? = null
    private var downloading: Job? = null

    init {
        viewModelScope.launch { updates.nudges.collect { check() } }
    }

    /**
     * Asks the server again. Called on every resume, and whenever a
     * submission is refused for being out of date.
     *
     * A check never interrupts a download or throws away a verified file
     * for the SAME release -- a worker who switched to Settings and back
     * must find the dialog where they left it.
     */
    fun check() {
        if (checking?.isActive == true) return
        checking = viewModelScope.launch {
            val required = updates.requiredRelease()
            val current = _state.value

            when {
                required == null -> {
                    downloading?.cancel()
                    updates.discardDownloads()
                    _state.value = UpdateState.Hidden
                }

                current is UpdateState.Shown && current.release.versionCode == required.versionCode -> {
                    // Back from the "Install unknown apps" switch with it on:
                    // carry straight on rather than make them tap twice.
                    if (current is UpdateState.NeedsPermission && installer.canInstall()) {
                        startDownload(required)
                    }
                }

                // First sighting, or the office published again on top.
                else -> {
                    downloading?.cancel()
                    _state.value = UpdateState.Available(required)
                }
            }
        }
    }

    fun onUpdateNow() {
        val release = (_state.value as? UpdateState.Shown)?.release ?: return
        if (installer.canInstall()) {
            startDownload(release)
        } else {
            _state.value = UpdateState.NeedsPermission(release)
        }
    }

    fun onAllowInstalls() {
        installer.openInstallPermission()
    }

    fun onRetry() = onUpdateNow()

    /**
     * Opens Android's installer. The state stays ReadyToInstall: if the
     * worker taps Cancel there, they come back to an Install button and
     * the 5 MB they already downloaded.
     */
    fun onInstall() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        installer.install(ready.apk)
    }

    private fun startDownload(release: AppRelease) {
        if (downloading?.isActive == true) return
        _state.value = UpdateState.Downloading(release, 0f)
        downloading = viewModelScope.launch {
            val result = updates.download(release) { progress ->
                // Reported from the IO thread; a straggler must not
                // overwrite a newer release the check has since shown.
                if ((_state.value as? UpdateState.Downloading)?.release == release) {
                    _state.value = UpdateState.Downloading(release, progress)
                }
            }
            _state.value = when (result) {
                is DownloadResult.Ready -> UpdateState.ReadyToInstall(release, result.file)
                is DownloadResult.Failed -> UpdateState.Failed(release, result.reason)
            }
        }
    }
}
