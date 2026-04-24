package dev.fuju.feature.auth.domain

import app.cash.turbine.test
import dev.fuju.core.domain.AuthStatus
import dev.fuju.core.domain.LoginResult
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStateMachineTest {

    private fun newMachine(
        actions: FakeAuthActions,
        config: AuthConfig = AuthConfig(disableSilentRefresh = true),
        scope: TestScope,
    ): AuthStateMachine = AuthStateMachine(actions, config, scope)

    @Test
    fun bootstrapTransitionsToAuthenticated() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions()
        val machine = newMachine(actions, scope = this)
        machine.bootstrap()
        assertEquals(AuthStatus.Authenticated, machine.state.value.status)
        assertEquals(actions.nextProfile, machine.state.value.user)
        assertEquals(1, actions.refreshCalls)
    }

    @Test
    fun bootstrapWith401MovesToUnauthenticated() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions(
            refreshBehavior = {
                throw AuthException(code = ErrorCode.TOKEN_INVALID, status = 401, message = "nope")
            },
        )
        val machine = newMachine(actions, scope = this)
        machine.bootstrap()
        assertEquals(AuthStatus.Unauthenticated, machine.state.value.status)
    }

    @Test
    fun loginSuccessAdoptsUser() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions()
        val machine = newMachine(actions, scope = this)
        val res = machine.login("alice", "hunter42")
        assertTrue(res is LoginResult.Authenticated)
        assertEquals(AuthStatus.Authenticated, machine.state.value.status)
        assertEquals(1, actions.loginCalls)
    }

    @Test
    fun loginRequiresMFAWhenFlagged() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions(
            loginBehavior = { _, _ ->
                LoginResponse(mfaRequired = true, preToken = "pt")
            },
        )
        val machine = newMachine(actions, scope = this)
        val res = machine.login("alice", "pw")
        assertEquals(LoginResult.MFARequired, res)
        assertEquals(AuthStatus.MFARequired, machine.state.value.status)
        assertTrue(machine.state.value.preTokenPresent)
    }

    @Test
    fun verifyMFAFailureIncrementsAttempts() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions(
            loginBehavior = { _, _ -> LoginResponse(mfaRequired = true, preToken = "pt") },
        )
        val machine = newMachine(actions, scope = this)
        machine.login("alice", "pw")
        assertFailsWith<AuthException> { machine.verifyMFA(code = "bad") }
        assertEquals(1, machine.state.value.mfaAttempts)
    }

    @Test
    fun logoutReturnsToUnauthenticated() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions()
        val machine = newMachine(actions, scope = this)
        machine.login("alice", "pw")
        machine.logout()
        assertEquals(AuthStatus.Unauthenticated, machine.state.value.status)
    }

    @Test
    fun stateFlowEmitsStatusSequence() = runTest(StandardTestDispatcher()) {
        val actions = FakeAuthActions()
        val machine = newMachine(actions, scope = this)
        machine.state.test {
            // Initial
            assertEquals(AuthStatus.Idle, awaitItem().status)
            machine.login("alice", "pw")
            // 遷移は copy ベースなので、同一 status の再 emit は飛ばされる
            assertEquals(AuthStatus.Authenticating, awaitItem().status)
            // 認証完了 (user 更新の emit)
            val final = awaitItem()
            assertEquals(AuthStatus.Authenticated, final.status)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
