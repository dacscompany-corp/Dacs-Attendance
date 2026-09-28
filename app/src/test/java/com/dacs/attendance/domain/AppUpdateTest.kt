package com.dacs.attendance.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdatePolicyTest {

    private fun release(code: Int) = AppRelease(
        versionCode = code,
        versionName = "0.$code.0",
        releaseNotes = null,
        downloadUrl = "https://example.test/$code.apk",
        sizeBytes = 10,
        sha256 = "00"
    )

    @Test
    fun `a newer release from the server is required`() {
        assertEquals(release(4), AppUpdatePolicy.required(3, Result.success(release(4)), null))
    }

    @Test
    fun `the same version is not an update`() {
        assertNull(AppUpdatePolicy.required(4, Result.success(release(4)), null))
    }

    @Test
    fun `an older published version never asks for a downgrade`() {
        assertNull(AppUpdatePolicy.required(5, Result.success(release(4)), null))
    }

    @Test
    fun `no release published at all is not an update`() {
        assertNull(AppUpdatePolicy.required(3, Result.success(null), null))
    }

    @Test
    fun `a failed check with nothing cached never blocks`() {
        assertNull(AppUpdatePolicy.required(3, Result.failure(IOException()), null))
    }

    @Test
    fun `a failed check keeps a release the phone already learned about`() {
        assertEquals(release(4), AppUpdatePolicy.required(3, Result.failure(IOException()), release(4)))
    }

    @Test
    fun `the server's answer beats a stale cache`() {
        // Cached 4 from yesterday, but the server now says 4 is withdrawn
        // and there is nothing newer than what is installed.
        assertNull(AppUpdatePolicy.required(3, Result.success(null), release(4)))
    }

    @Test
    fun `a cached release is dropped once this build has caught up`() {
        assertNull(AppUpdatePolicy.required(4, Result.failure(IOException()), release(4)))
    }
}

class ApkVerificationTest {

    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun copy(
        source: ByteArray,
        size: Long = bytes.size.toLong(),
        hash: String = sha,
        progress: MutableList<Float> = mutableListOf()
    ): CopyResult = ApkVerification.copyVerifying(
        ByteArrayInputStream(source),
        ByteArrayOutputStream(),
        size,
        hash,
        onProgress = { progress += it }
    )

    @Test
    fun `the exact published file verifies`() {
        assertEquals(CopyResult.Verified, copy(bytes))
    }

    @Test
    fun `an upper-case hash from elsewhere still matches`() {
        assertEquals(CopyResult.Verified, copy(bytes, hash = sha.uppercase()))
    }

    @Test
    fun `a download cut short is the wrong size`() {
        assertEquals(CopyResult.WrongSize, copy(bytes.copyOf(150_000)))
    }

    @Test
    fun `a longer file is refused without reading all of it`() {
        assertEquals(CopyResult.WrongSize, copy(bytes, size = 100_000))
    }

    @Test
    fun `same size but different bytes is the wrong hash`() {
        val tampered = bytes.copyOf().also { it[1234] = 0x7f }
        assertEquals(CopyResult.WrongHash, copy(tampered))
    }

    @Test
    fun `progress climbs to exactly one and is reported in whole percents`() {
        val progress = mutableListOf<Float>()
        copy(bytes, progress = progress)
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress)
        assertEquals(progress.distinct(), progress)
    }
}
