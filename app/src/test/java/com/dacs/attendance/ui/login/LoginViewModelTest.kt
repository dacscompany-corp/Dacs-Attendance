package com.dacs.attendance.ui.login

import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.LoginRejected
import com.dacs.attendance.domain.Eligibility
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val worker = WorkerProfile(
        id = "11111111-1111-1111-1111-111111111111",
        email = "juan@dacsbuilding.com",
        displayName = "Juan dela Cruz",
        position = "Mason",
        workerNo = 42,
        role = "worker",
        status = "active"
    )

    private class FakeAuth(
        private val result: Result<WorkerProfile>
    ) : AuthRepository {
        var calls = 0
            private set
        var lastEmail: String? = null
            private set

        override suspend fun signIn(email: String, password: String): Result<WorkerProfile> {
            calls++
            lastEmail = email
            return result
        }

        override suspend fun signOut() = Unit
        override suspend fun currentWorker(): WorkerProfile? = result.getOrNull()
    }

    @Test
    fun `an empty field never reaches the network`() = runTest {
        val auth = FakeAuth(Result.success(worker))
        val vm = LoginViewModel(auth)

        vm.onEmailChange("juan@dacsbuilding.com")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(0, auth.calls)
        assertEquals(LoginFailure.MissingFields, vm.uiState.value.failure)
    }

    @Test
    fun `the email is trimmed before it is sent`() = runTest {
        // Workers type on a phone keyboard in the sun; a trailing space is
        // an authentication failure they cannot see.
        val auth = FakeAuth(Result.success(worker))
        val vm = LoginViewModel(auth)

        vm.onEmailChange("  juan@dacsbuilding.com ")
        vm.onPasswordChange("secret123")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals("juan@dacsbuilding.com", auth.lastEmail)
    }

    @Test
    fun `a rejected sign-in shows the mapped failure and stops the spinner`() = runTest {
        val auth = FakeAuth(Result.failure(LoginRejected(LoginFailure.WrongCredentials)))
        val vm = LoginViewModel(auth)

        vm.onEmailChange("juan@dacsbuilding.com")
        vm.onPasswordChange("wrong")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(LoginFailure.WrongCredentials, vm.uiState.value.failure)
        assertTrue(!vm.uiState.value.submitting)
    }

    @Test
    fun `a deactivated worker is told the account is inactive`() = runTest {
        val auth = FakeAuth(Result.failure(LoginRejected(LoginFailure.AccountInactive)))
        val vm = LoginViewModel(auth)

        vm.onEmailChange("juan@dacsbuilding.com")
        vm.onPasswordChange("secret123")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(LoginFailure.AccountInactive, vm.uiState.value.failure)
    }

    @Test
    fun `editing a field clears the previous failure`() = runTest {
        val auth = FakeAuth(Result.failure(LoginRejected(LoginFailure.WrongCredentials)))
        val vm = LoginViewModel(auth)

        vm.onEmailChange("juan@dacsbuilding.com")
        vm.onPasswordChange("wrong")
        vm.onSubmit()
        advanceUntilIdle()
        vm.onPasswordChange("wrong2")

        assertNull(vm.uiState.value.failure)
    }

    @Test
    fun `a successful sign-in reports the signed-in worker`() = runTest {
        val auth = FakeAuth(Result.success(worker))
        val vm = LoginViewModel(auth)

        vm.onEmailChange("juan@dacsbuilding.com")
        vm.onPasswordChange("secret123")
        vm.onSubmit()
        advanceUntilIdle()

        assertNull(vm.uiState.value.failure)
        assertEquals(worker, vm.uiState.value.signedIn)
        assertEquals(Eligibility.Allowed, worker.eligibility())
    }
}
