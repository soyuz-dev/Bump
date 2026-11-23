package org.soyuz.engine.timing

import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.scene.Scene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SceneTimersTest {
    @Test
    fun `scene interface exposes cancellable timer methods`() {
        val scene: Scene = RuntimeScene("game")
        val calls = mutableListOf<String>()
        val after = scene.after(1.seconds) { calls += "after" }
        val during = scene.during(1.seconds) { calls += "during:$it" }
        val interval = scene.forEvery(1.seconds) { calls += "interval" }
        val frame = scene.everyFrame { calls += "frame:$it" }

        scene.updateTimers(1.seconds)

        assertEquals(listOf("after", "during:1.0", "interval", "frame:1.0"), calls)
        assertFalse(after.isActive)
        assertFalse(during.isActive)
        assertTrue(interval.isActive)
        assertTrue(frame.isActive)
        interval.cancel()
        frame.cancel()
        scene.updateTimers(1.seconds)
        assertEquals(4, calls.size)
    }

    @Test
    fun `inactive scene preserves elapsed time while another scene advances`() {
        val game = RuntimeScene("game")
        val menu = RuntimeScene("menu")
        var completed = 0
        val progress = mutableListOf<Double>()
        game.after(1.seconds) { completed++ }
        game.during(1.seconds) { progress += it }
        var menuFrames = 0
        menu.everyFrame { menuFrames++ }

        game.updateTimers(400.milliseconds)
        menu.updateTimers(10.seconds)
        assertEquals(0, completed)
        assertEquals(listOf(0.4), progress)
        assertEquals(1, menuFrames)

        game.updateTimers(600.milliseconds)
        assertEquals(1, completed)
        assertEquals(listOf(0.4, 1.0), progress)
    }

    @Test
    fun `inactive scene does not advance pending registrations`() {
        val scene = RuntimeScene("game")
        var calls = 0
        val timer = scene.after(1.seconds) { calls++ }

        scene.updateTimers(10.seconds) { false }
        assertEquals(0, calls)
        assertTrue(timer.isActive)
        scene.updateTimers(500.milliseconds)
        assertEquals(0, calls)
        scene.updateTimers(500.milliseconds)
        assertEquals(1, calls)
    }

    @Test
    fun `scene cleanup cancels active and pending timers`() {
        val scene = RuntimeScene("game")
        var calls = 0
        val active = listOf(
            scene.after(10.seconds) { calls++ },
            scene.during(10.seconds) { calls++ },
            scene.forEvery(10.seconds) { calls++ },
            scene.everyFrame { calls++ }
        )
        scene.updateTimers(1.seconds)
        val pending = scene.after(Duration.ZERO) { calls++ }

        scene.cleanup()
        scene.cleanup()
        scene.updateTimers(20.seconds)

        assertEquals(2, calls)
        assertTrue(active.none { it.isActive })
        assertFalse(pending.isActive)
    }

    @Test
    fun `cleanup inside scene callback prevents remaining callbacks`() {
        val scene = RuntimeScene("game")
        var calls = 0
        lateinit var pending: Subscription
        scene.after(Duration.ZERO) {
            pending = scene.after(Duration.ZERO) { calls++ }
            scene.cleanup()
        }
        val later = scene.everyFrame { calls++ }

        scene.updateTimers(1.seconds)
        scene.updateTimers(1.seconds)

        assertEquals(0, calls)
        assertFalse(later.isActive)
        assertFalse(pending.isActive)
    }

    @Test
    fun `scene switch stops interval catch up and later callbacks until scene resumes`() {
        val game = RuntimeScene("game")
        val menu = RuntimeScene("menu")
        var active: Scene = game
        var intervalCalls = 0
        var laterCalls = 0
        var menuCalls = 0
        val interval = game.forEvery(100.milliseconds) {
            intervalCalls++
            active = menu
        }
        game.everyFrame { laterCalls++ }
        menu.everyFrame { menuCalls++ }

        game.updateTimers(350.milliseconds) { active === game }
        assertEquals(1, intervalCalls)
        assertEquals(0, laterCalls)
        assertEquals(0, menuCalls)
        assertTrue(interval.isActive)

        menu.updateTimers(1.seconds) { active === menu }
        assertEquals(1, menuCalls)

        interval.cancel()
        active = game
        game.updateTimers(50.milliseconds) { active === game }
        assertEquals(1, laterCalls)
    }

    @Test
    fun `timers registered by scene callbacks wait until next scene update`() {
        val scene = RuntimeScene("game")
        var calls = 0
        scene.after(Duration.ZERO) {
            scene.everyFrame { calls++ }
            scene.after(Duration.ZERO) { calls++ }
        }

        scene.updateTimers(1.seconds)
        assertEquals(0, calls)
        scene.updateTimers(1.seconds)
        assertEquals(2, calls)
    }
}
