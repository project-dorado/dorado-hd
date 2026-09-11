package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BeanstalkEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeanstalkTest {

    private fun playing(state: BeanstalkEngine.State): BeanstalkEngine.State =
        state.copy(phase = BeanstalkEngine.Phase.PLAYING, countdownMs = 0L)

    private fun stepAll(state: BeanstalkEngine.State, frames: Int, input: BeanstalkEngine.Input = BeanstalkEngine.Input()): BeanstalkEngine.State {
        var current = state
        repeat(frames) { current = BeanstalkEngine.step(current, input, 16L) }
        return current
    }

    @Test
    fun `spec constants match the synthesized cadence and physics`() {
        assertEquals(1_500f, BeanstalkEngine.SPECIAL_CADENCE, 0f)
        assertEquals(8_000f, BeanstalkEngine.BOSS_CADENCE, 0f)
        assertEquals(0.001f, BeanstalkEngine.GRAVITY, 0f)
        assertEquals(-0.5f, BeanstalkEngine.JUMP_VELOCITY, 0f)
        assertEquals(-0.9f, BeanstalkEngine.JUMPPAD_VELOCITY, 0f)
        assertEquals(-0.4f, BeanstalkEngine.HOP_VELOCITY, 0f)
        assertEquals(200L, BeanstalkEngine.BOW_COOLDOWN_MS)
        assertEquals(10_000L, BeanstalkEngine.SHIELD_MS)
        assertEquals(5_000L, BeanstalkEngine.BALLOON_MS)
        assertEquals(520f, BeanstalkEngine.DEATH_SCREEN_Y, 0f)
        assertEquals(720f, BeanstalkEngine.GAME_OVER_SCREEN_Y, 0f)
    }

    @Test
    fun `a jump rises about one hundred and twenty five pixels and lands`() {
        val start = playing(BeanstalkEngine.newRun(seed = 5))
        val grounded = stepAll(start, 2)
        val startY = grounded.pig.y
        var current = BeanstalkEngine.step(grounded, BeanstalkEngine.Input(tapScreenX = 100f, tapScreenY = 200f), 16L)
        assertTrue(current.pig.vy < 0f)
        var apex = current.pig.y
        var landedAt = -1
        for (i in 0 until 120) {
            current = BeanstalkEngine.step(current, BeanstalkEngine.Input(), 16L)
            apex = minOf(apex, current.pig.y)
            if (i > 5 && current.pig.grounded) {
                landedAt = i
                break
            }
        }
        val rise = startY - apex
        assertTrue("rise was $rise", rise in 112f..132f)
        assertTrue("landed at $landedAt", landedAt in 15..100)
    }

    @Test
    fun `accumulated gravity matches semi implicit integration`() {
        var current = playing(BeanstalkEngine.newRun(seed = 9))
        current = current.copy(pig = current.pig.copy(y = 100f, vy = 0f, grounded = false), leaves = emptyList(), cameraTop = -300f)
        current = BeanstalkEngine.step(current, BeanstalkEngine.Input(), 10L)
        assertEquals(0.01f, current.pig.vy, 0.0001f)
        assertEquals(100.1f, current.pig.y, 0.001f)
    }

    @Test
    fun `leaf placement stays inside the authored bounds`() {
        var current = playing(BeanstalkEngine.newRun(seed = 21))
        current = stepAll(current, 400)
        assertTrue(current.leaves.size >= 4)
        current.leaves.forEach { leaf ->
            assertTrue("leaf x ${leaf.x}", leaf.x in BeanstalkEngine.LEAF_MIN_X..BeanstalkEngine.LEAF_MAX_X)
        }
        for (i in 1 until current.leaves.size) {
            val gap = abs(current.leaves[i - 1].y - current.leaves[i].y)
            if (gap > 1f && current.leaves[i].kind == BeanstalkEngine.LeafKind.NORMAL) {
                assertTrue("gap $gap", gap <= BeanstalkEngine.LEAF_GAP_MAX + 1f)
            }
        }
    }

    @Test
    fun `specials queue at the fifteen hundred pixel cadence`() {
        val base = playing(BeanstalkEngine.newRun(seed = 3)).let {
            it.copy(minPigY = BeanstalkEngine.START_LINE - 1_501f, pig = it.pig.copy(y = BeanstalkEngine.START_LINE - 1_501f))
        }
        val next = BeanstalkEngine.step(base, BeanstalkEngine.Input(), 16L)
        assertEquals(1, next.specialsSpawned)
        assertEquals(3_000f, next.nextSpecialAt, 0.5f)
        assertTrue(next.plans.isNotEmpty() || next.enemies.isNotEmpty())
    }

    @Test
    fun `bosses queue at the eight thousand pixel cadence`() {
        val base = playing(BeanstalkEngine.newRun(seed = 11)).let {
            it.copy(minPigY = BeanstalkEngine.START_LINE - 8_001f, pig = it.pig.copy(y = BeanstalkEngine.START_LINE - 8_001f))
        }
        val next = BeanstalkEngine.step(base, BeanstalkEngine.Input(), 16L)
        assertEquals(1, next.bossesSpawned)
        assertEquals(BeanstalkEngine.BOSS_GROUND_COUNT, next.plans.count { it.kind == BeanstalkEngine.LeafKind.BOSS_GROUND })
        assertNotNull(next.pendingBoss)
        assertEquals(16_000f, next.nextBossAt, 0.5f)
    }

    @Test
    fun `food pickups pay five hundred plus fifty per pattern`() {
        assertEquals(500, BeanstalkEngine.foodWorth(0))
        assertEquals(1_250, BeanstalkEngine.foodWorth(15))
        assertEquals(650, BeanstalkEngine.foodWorth(3))
        assertEquals(500, BeanstalkEngine.foodWorth(-2))
        assertEquals(1_250, BeanstalkEngine.foodWorth(99))
    }

    @Test
    fun `arrows account hits kills and misses`() {
        val base = playing(BeanstalkEngine.newRun(seed = 17)).copy(
            leaves = emptyList(),
            enemies = listOf(BeanstalkEngine.Enemy(1, BeanstalkEngine.EnemyKind.WASP, 110f, 100f)),
            arrows = listOf(BeanstalkEngine.Arrow(1, 100f, 100f, 0.4f, 0f)),
        )
        val hit = BeanstalkEngine.step(base, BeanstalkEngine.Input(), 16L)
        assertEquals(1, hit.stats.arrowsHit)
        assertEquals(0, hit.stats.arrowsMissed)
        assertEquals(1, hit.stats.kills[BeanstalkEngine.EnemyKind.WASP])
        assertTrue(hit.enemies.isEmpty())

        val miss = BeanstalkEngine.step(base.copy(arrows = listOf(BeanstalkEngine.Arrow(1, -6f, 100f, -0.4f, 0f))), BeanstalkEngine.Input(), 16L)
        assertEquals(0, miss.stats.arrowsHit)
        assertEquals(1, miss.stats.arrowsMissed)
    }

    @Test
    fun `dying with arrows in flight counts every arrow as a miss`() {
        val base = playing(BeanstalkEngine.newRun(seed = 17)).copy(
            leaves = emptyList(),
            pig = BeanstalkEngine.Pig(120f, 560f, grounded = false),
            cameraTop = -50f,
            arrows = listOf(
                BeanstalkEngine.Arrow(1, 100f, 100f, 0f, 0f),
                BeanstalkEngine.Arrow(2, 120f, 120f, 0f, 0f),
            ),
        )
        val next = BeanstalkEngine.step(base, BeanstalkEngine.Input(), 16L)
        assertEquals(BeanstalkEngine.Phase.DYING, next.phase)
        assertEquals(2, next.stats.arrowsMissed)
        assertTrue(next.arrows.isEmpty())
    }

    @Test
    fun `safe pickup patterns stay inside the sixteen pattern space`() {
        val pickups = ArrayList<BeanstalkEngine.Leaf>()
        for (seed in 1..30) {
            var current = playing(BeanstalkEngine.newRun(seed = seed))
            repeat(300) {
                current = BeanstalkEngine.step(current, BeanstalkEngine.Input(), 16L)
                pickups += current.leaves.filter { it.pickup != null }
            }
        }
        assertTrue("expected some pickups", pickups.isNotEmpty())
        pickups.forEach { leaf ->
            assertTrue(leaf.pickupPattern in 0 until BeanstalkEngine.FOOD_PATTERNS)
            assertTrue(leaf.pickup!! in BeanstalkEngine.PickupKind.values())
        }
    }

    @Test
    fun `score is height above the start line plus bonus`() {
        val base = playing(BeanstalkEngine.newRun(seed = 1)).let {
            it.copy(minPigY = BeanstalkEngine.START_LINE - 1_234f, bonus = 650)
        }
        assertEquals(1_884, base.score)
    }

    @Test
    fun `cups award tiers once and queue banners`() {
        val first = BeanstalkEngine.mergeRun(
            BeanstalkEngine.Cups(),
            BeanstalkEngine.RunStats(jumps = 100, shields = 10),
            10_000,
        )
        assertEquals(1, first.tiers[BeanstalkEngine.CupCounter.JUMPS])
        assertEquals(1, first.tiers[BeanstalkEngine.CupCounter.SHIELDS])
        assertEquals(1, first.tiers[BeanstalkEngine.CupCounter.HEIGHT])
        assertEquals(3, first.banners.size)
        val second = BeanstalkEngine.mergeRun(first, BeanstalkEngine.RunStats(jumps = 50), 0)
        assertEquals(1, second.tiers[BeanstalkEngine.CupCounter.JUMPS])
        assertEquals(first.banners.size, second.banners.size)
        assertEquals(2, BeanstalkEngine.tierFor(BeanstalkEngine.CupCounter.JUMPS, 500))
        assertEquals(5, BeanstalkEngine.tierFor(BeanstalkEngine.CupCounter.HEIGHT, 5_000_000))
    }

    @Test
    fun `banners count down and disappear`() {
        val cups = BeanstalkEngine.Cups(banners = listOf(BeanstalkEngine.Banner(BeanstalkEngine.CupCounter.JUMPS, 1, 300L)))
        val half = BeanstalkEngine.advanceBanners(cups, 200L)
        assertEquals(100L, half.banners.first().remainingMs)
        val gone = BeanstalkEngine.advanceBanners(half, 200L)
        assertTrue(gone.banners.isEmpty())
    }

    @Test
    fun `cup profile encode and decode round trip`() {
        val cups = BeanstalkEngine.mergeRun(
            BeanstalkEngine.Cups(),
            BeanstalkEngine.RunStats(jumps = 2_600, arrowsFired = 120),
            250_000,
        )
        val restored = BeanstalkEngine.decodeCups(BeanstalkEngine.encodeCups(cups))
        assertEquals(cups.values, restored.values)
        assertEquals(cups.tiers, restored.tiers)
        assertTrue(restored.tiers[BeanstalkEngine.CupCounter.FLOWER_JUMPS] == null)
    }

    @Test
    fun `decode rejects malformed cup blobs`() {
        assertEquals(BeanstalkEngine.Cups(), BeanstalkEngine.decodeCups("garbage"))
        assertEquals(BeanstalkEngine.Cups(), BeanstalkEngine.decodeCups(null))
        val next = BeanstalkEngine.decodeCups("v1|HEIGHT:oops|")
        assertEquals(1, next.values.size)
        assertEquals(0, next.values[BeanstalkEngine.CupCounter.HEIGHT])
        assertNull(BeanstalkEngine.decodeCups("v1|HEIGHT:10|").tiers[BeanstalkEngine.CupCounter.HEIGHT])
    }

    @Test
    fun `countdown runs three two one go and starts the run`() {
        var current = BeanstalkEngine.newRun(seed = 4)
        assertEquals(BeanstalkEngine.Phase.COUNTDOWN, current.phase)
        var sawStart = false
        repeat(200) {
            current = BeanstalkEngine.step(current, BeanstalkEngine.Input(), 16L)
            if (BeanstalkEngine.Event.START in current.events) sawStart = true
        }
        assertTrue(sawStart)
        assertEquals(BeanstalkEngine.Phase.PLAYING, current.phase)
        assertEquals(1, current.stats.starts)
    }

    @Test
    fun `falling past the death line opens game over at the far line`() {
        var current = playing(BeanstalkEngine.newRun(seed = 6)).copy(
            leaves = emptyList(),
            pig = BeanstalkEngine.Pig(120f, 500f, vy = 0.5f, grounded = false),
            cameraTop = -50f,
        )
        var died = false
        var over = false
        repeat(400) {
            current = BeanstalkEngine.step(current, BeanstalkEngine.Input(), 16L)
            if (current.phase == BeanstalkEngine.Phase.DYING) died = true
            if (current.phase == BeanstalkEngine.Phase.GAME_OVER) {
                over = true
            }
        }
        assertTrue(died)
        assertTrue(over)
    }
}
