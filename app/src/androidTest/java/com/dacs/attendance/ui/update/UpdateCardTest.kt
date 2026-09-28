package com.dacs.attendance.ui.update

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dacs.attendance.domain.AppRelease
import com.dacs.attendance.domain.UpdateFailure
import com.dacs.attendance.ui.theme.AttendanceTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The update card in each state: the right words, the right single
 * action, and the bilingual failure copy.
 *
 * Each test also saves a PNG to the app's external files dir
 * (update-card-<state>.png) so the design can be eyeballed with
 * `adb pull` without publishing a real release.
 */
@RunWith(AndroidJUnit4::class)
class UpdateCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val release = AppRelease(
        versionCode = 4,
        versionName = "0.4.0",
        releaseNotes = "Fixed the camera on older phones.",
        downloadUrl = "https://example.test/4.apk",
        sizeBytes = 5_000_000,
        sha256 = "0".repeat(64)
    )

    private var clicks = mutableListOf<String>()

    private fun show(state: UpdateState.Shown, name: String) {
        compose.setContent {
            AttendanceTheme {
                UpdateCard(
                    state = state,
                    onUpdateNow = { clicks += "update" },
                    onAllowInstalls = { clicks += "allow" },
                    onInstall = { clicks += "install" },
                    onRetry = { clicks += "retry" }
                )
            }
        }
        compose.waitForIdle()
        save(name)
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "update-card-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Available: the reference design's copy, the notes, and UPDATE NOW!. */
    @Test
    fun availableShowsTitleNotesAndUpdateNow() {
        show(UpdateState.Available(release), "available")
        compose.onNodeWithText("New Update is Available").assertIsDisplayed()
        compose.onNodeWithText("A new version is released, please update to get new features").assertIsDisplayed()
        compose.onNodeWithText("Fixed the camera on older phones.").assertIsDisplayed()
        compose.onNodeWithText("Version 0.4.0").assertIsDisplayed()
        compose.onNodeWithText("UPDATE NOW!").performClick()
        assertEquals(listOf("update"), clicks)
    }

    /** NeedsPermission: explains the switch, and the button opens it. */
    @Test
    fun needsPermissionAsksToAllowInstalls() {
        show(UpdateState.NeedsPermission(release), "permission")
        compose.onNodeWithText("One more step").assertIsDisplayed()
        compose.onNodeWithText("ALLOW INSTALLS").performClick()
        assertEquals(listOf("allow"), clicks)
    }

    /** Downloading: percent on a disabled button. */
    @Test
    fun downloadingShowsPercentAndCannotBeTapped() {
        show(UpdateState.Downloading(release, 0.45f), "downloading")
        compose.onNodeWithText("DOWNLOADING… 45%").assertIsNotEnabled()
    }

    /** ReadyToInstall: INSTALL hands over to Android. */
    @Test
    fun readyOffersInstall() {
        show(UpdateState.ReadyToInstall(release, File("x.apk")), "ready")
        compose.onNodeWithText("INSTALL").performClick()
        assertEquals(listOf("install"), clicks)
    }

    /** Failed: English AND Tagalog, and TRY AGAIN. */
    @Test
    fun failedIsBilingualWithTryAgain() {
        show(UpdateState.Failed(release, UpdateFailure.DownloadFailed), "failed")
        compose.onNodeWithText("Download failed. Check your internet and try again.").assertIsDisplayed()
        compose.onNodeWithText("Hindi natapos ang pag-download. Tingnan ang internet at subukan ulit.").assertIsDisplayed()
        compose.onNodeWithText("TRY AGAIN").performClick()
        assertEquals(listOf("retry"), clicks)
    }

    /** A damaged file has its own words, also bilingual. */
    @Test
    fun damagedFileHasItsOwnBilingualCopy() {
        show(UpdateState.Failed(release, UpdateFailure.FileDamaged), "damaged")
        compose.onNodeWithText("The update file was damaged. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Sira ang update file. Subukan ulit.").assertIsDisplayed()
    }
}
