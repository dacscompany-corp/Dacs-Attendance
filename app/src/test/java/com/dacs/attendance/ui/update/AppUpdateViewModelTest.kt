package com.dacs.attendance.ui.update

import com.dacs.attendance.data.local.InstallGateway
import com.dacs.attendance.data.repo.AppUpdateRepository
import com.dacs.attendance.data.repo.DownloadResult
import com.dacs.attendance.domain.AppRelease
import com.dacs.attendance.domain.UpdateFailure
import com.dacs.attendance.support.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun release(code: Int) = AppRelease(
        versionCode = code,
        versionName = "0.$code.0",
        releaseNotes = "Fixed the camera",
        downloadUrl = "https://example.test/$code.apk",
        sizeBytes = 10,
        sha256 = "00"
    )

    private val apk = File("dacs-attendance-4.apk")

    private class FakeUpdates : AppUpdateRepository {
        var required: AppRelease? = null
        var downloads = 0
        var discards = 0
        var nextDownload: DownloadResult = DownloadResult.Ready(File("x.apk"))
        var gate: CompletableDeferred<Unit>? = null
        private val _nudges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val nudges: SharedFlow<Unit> = _nudges

        override suspend fun requiredRelease(): AppRelease? = required

        override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): DownloadResult {
            downloads++
            onProgress(0.5f)
            gate?.await()
            return nextDownload
        }

        override fun discardDownloads() {
            discards++
        }

        fun nudge() {
            _nudges.tryEmit(Unit)
        }
    }

    private class FakeInstaller(var allowed: Boolean = true) : InstallGateway {
        var permissionOpened = 0
        val installed = mutableListOf<File>()
        override fun canInstall() = allowed
        override fun openInstallPermission() {
            permissionOpened++
        }
        override fun install(apk: File) {
            installed += apk
        }
    }

    private val updates = FakeUpdates()
    private val installer = FakeInstaller()

    private fun vm() = AppUpdateViewModel(updates, installer)

    @Test
    fun `a current build never shows the dialog`() = runTest {
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateState.Hidden, vm.state.value)
    }

    @Test
    fun `a newer release shows the dialog`() = runTest {
        updates.required = release(4)
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateState.Available(release(4)), vm.state.value)
    }

    @Test
    fun `a refused submission re-checks without waiting for a resume`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        updates.required = release(4)
        updates.nudge()
        advanceUntilIdle()
        assertEquals(UpdateState.Available(release(4)), vm.state.value)
    }

    @Test
    fun `update now downloads and ends ready to install`() = runTest {
        updates.required = release(4)
        updates.nextDownload = DownloadResult.Ready(apk)
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()
        assertEquals(UpdateState.ReadyToInstall(release(4), apk), vm.state.value)
    }

    @Test
    fun `progress is visible while the download runs`() = runTest {
        updates.required = release(4)
        updates.gate = CompletableDeferred()
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()
        assertEquals(UpdateState.Downloading(release(4), 0.5f), vm.state.value)
        updates.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.state.value is UpdateState.ReadyToInstall)
    }

    @Test
    fun `without the install switch the worker is sent to turn it on first`() = runTest {
        updates.required = release(4)
        installer.allowed = false
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()
        assertEquals(UpdateState.NeedsPermission(release(4)), vm.state.value)
        assertEquals(0, updates.downloads)

        vm.onAllowInstalls()
        assertEquals(1, installer.permissionOpened)
    }

    @Test
    fun `coming back with the switch on carries straight on to the download`() = runTest {
        updates.required = release(4)
        installer.allowed = false
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()

        installer.allowed = true
        vm.check() // ON_RESUME after Settings
        advanceUntilIdle()
        assertEquals(1, updates.downloads)
        assertTrue(vm.state.value is UpdateState.ReadyToInstall)
    }

    @Test
    fun `a failed download offers try again, which downloads afresh`() = runTest {
        updates.required = release(4)
        updates.nextDownload = DownloadResult.Failed(UpdateFailure.DownloadFailed)
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()
        assertEquals(UpdateState.Failed(release(4), UpdateFailure.DownloadFailed), vm.state.value)

        updates.nextDownload = DownloadResult.Ready(apk)
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(2, updates.downloads)
        assertEquals(UpdateState.ReadyToInstall(release(4), apk), vm.state.value)
    }

    @Test
    fun `cancelling the installer keeps the download for another try`() = runTest {
        updates.required = release(4)
        updates.nextDownload = DownloadResult.Ready(apk)
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()

        vm.onInstall()
        vm.check() // back from the installer's Cancel
        advanceUntilIdle()
        vm.onInstall()

        assertEquals(listOf(apk, apk), installer.installed)
        assertEquals(1, updates.downloads)
        assertEquals(UpdateState.ReadyToInstall(release(4), apk), vm.state.value)
    }

    @Test
    fun `a release published on top of a pending one replaces it`() = runTest {
        updates.required = release(4)
        updates.nextDownload = DownloadResult.Ready(apk)
        val vm = vm()
        vm.check()
        advanceUntilIdle()
        vm.onUpdateNow()
        advanceUntilIdle()

        updates.required = release(5)
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateState.Available(release(5)), vm.state.value)
    }

    @Test
    fun `once current the dialog goes and old downloads are deleted`() = runTest {
        updates.required = release(4)
        val vm = vm()
        vm.check()
        advanceUntilIdle()

        updates.required = null
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateState.Hidden, vm.state.value)
        assertTrue(updates.discards > 0)
    }
}
