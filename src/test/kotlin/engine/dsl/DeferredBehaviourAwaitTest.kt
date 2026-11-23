package org.soyuz.engine.dsl

import kotlinx.coroutines.CancellationException
import org.soyuz.engine.behaviour.BehaviourHandle
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DeferredBehaviourAwaitTest {
    @Test
    fun `await before entity installation waits without starting behaviour`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var started = false
        var awaited = false
        val target = scope.launch {
            started = true
            wait(Duration.ZERO)
        }
        scene.launch {
            target.await()
            awaited = true
        }

        scene.updateTimers(10.seconds)
        assertFalse(started)
        assertFalse(awaited)

        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)
        assertTrue(started)
        assertFalse(awaited)
        scene.updateTimers(1.seconds)
        assertTrue(awaited)
    }

    @Test
    fun `preinstallation waiter can catch immediate failure after installation`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        val failure = IllegalStateException("entity behaviour failed")
        val target = scope.launch { throw failure }
        var caught: Throwable? = null
        scene.launch {
            try {
                target.await()
            } catch (cause: IllegalStateException) {
                caught = cause
            }
        }
        scene.updateTimers(1.seconds)
        scene.addEntity(entity)
        scope.installBehaviour(scene)

        scene.updateTimers(1.seconds)
        scene.updateTimers(1.seconds)

        assertSame(failure, caught)
        assertFalse(target.isActive)
    }

    @Test
    fun `cancellation before installation releases waiters and prevents body`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var started = false
        var cancelled = false
        val target = scope.launch { started = true }
        scene.launch {
            try {
                target.await()
            } catch (_: CancellationException) {
                cancelled = true
            }
        }

        scene.updateTimers(1.seconds)
        target.cancel()
        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)

        assertTrue(cancelled)
        assertFalse(started)
        assertFalse(target.isActive)
    }

    @Test
    fun `discarded configuration releases waiters`() {
        val scene = RuntimeScene("game")
        val scope = EntityScope(DefaultGameEntity("bomb"))
        val target = scope.launch { error("Must not run") }
        var cancelled = false
        scene.launch {
            try {
                target.await()
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        scene.updateTimers(1.seconds)

        scope.discard()
        scene.updateTimers(1.seconds)

        assertTrue(cancelled)
        assertFalse(target.isActive)
    }

    @Test
    fun `cancelling preinstallation waiter leaves target available for installation`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var ran = false
        val target = scope.launch { ran = true }
        val waiter = scene.launch { target.await() }
        scene.updateTimers(1.seconds)

        waiter.cancel()
        assertTrue(target.isActive)
        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)

        assertTrue(ran)
        assertFalse(target.isActive)
        assertFalse(waiter.isActive)
    }

    @Test
    fun `entity deferred handle rejects self await after binding`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        lateinit var target: BehaviourHandle
        var rejected = false
        target = scope.launch {
            try {
                target.await()
            } catch (_: IllegalStateException) {
                rejected = true
            }
        }
        scene.addEntity(entity)
        scope.installBehaviour(scene)

        scene.updateTimers(1.seconds)

        assertTrue(rejected)
        assertFalse(target.isActive)
    }
}
