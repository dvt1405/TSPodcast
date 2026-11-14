package tss.t.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Player placements (docs/in-progress/plans/player-ads-and-premium.md §3): policy, Up next rows,
 * strip / refresh / pause rules and settings. Same cases as shared `PlayerAdsTest` (iOS).
 */
class PlayerAdsTest {

    private class FakeStore : AdCounterStore {
        var install = 0L
        override fun installAt(now: Long) = install
        override var sessionCount = 5
        override var lastFullScreenAt = 0L
        override var adFreeUntil = 0L
        val last = mutableMapOf<AdPlacement, Long>()
        val session = mutableMapOf<AdPlacement, Int>()
        override fun lastShownAt(placement: AdPlacement) = last[placement] ?: 0L
        override fun shownOnDay(placement: AdPlacement, day: Long) = 0
        override fun shownThisSession(placement: AdPlacement) = session[placement] ?: 0
        override fun recordShown(placement: AdPlacement, now: Long, day: Long) {
            last[placement] = now
            session[placement] = shownThisSession(placement) + 1
        }

        override fun startSession() {
            sessionCount++
        }

        override fun grantAdFreeUntil(until: Long) {
            adFreeUntil = until
        }
    }

    private val day0 = 1_800_000_000_000L
    private var now = day0
    private var premium = false
    private lateinit var store: FakeStore
    private lateinit var policy: AdPolicy

    private val defaults = AdsSettings.defaults(debug = false)
    private val on = defaults.copy(
        placementsEnabled = defaults.placementsEnabled +
                (AdPlacement.PlayerNowPlaying to true) +
                (AdPlacement.PlayerUpNext to true) +
                (AdPlacement.PlayerPause to true),
    )

    private val tall = PlayerAdContext(canRequestAds = true, windowHeightDp = 800)
    private val paused = tall.copy(manualPauseMs = 6_000L)

    @Before
    fun setUp() {
        store = FakeStore().apply { install = day0 - 7 * AdPolicy.DAY_MS }
        policy = AdPolicy(
            store,
            clock = { now },
            timeZone = { TimeZone.getTimeZone("UTC") },
            isPremium = { premium },
        )
    }

    private fun decide(placement: AdPlacement, ctx: PlayerAdContext = tall, s: AdsSettings = on) =
        policy.canShowPlayerInline(placement, s, ctx)

    // Settings and template defaults.

    @Test
    fun `player placements are native, off by default and flagged as player`() {
        val player = listOf(AdPlacement.PlayerNowPlaying, AdPlacement.PlayerUpNext, AdPlacement.PlayerPause)
        assertEquals(listOf("player_now_playing", "player_up_next", "player_pause"), player.map { it.key })
        player.forEach {
            assertEquals(AdFormat.Native, it.format)
            assertTrue(it.isPlayer)
            assertFalse(defaults.isEnabled(it))
            assertFalse(AdsSettings.defaults(debug = true).isEnabled(it))
        }
        assertFalse(AdPlacement.PlayerEpisodeList.isPlayer)
        assertFalse(defaults.isEnabled(AdPlacement.PlayerEpisodeList))
    }

    @Test
    fun `player settings defaults and parsing`() {
        assertTrue(defaults.playerRespectGrace)
        assertFalse(AdsSettings.defaults(debug = true).playerRespectGrace)
        assertEquals(640, defaults.playerMinHeightDp)
        assertEquals(60L, defaults.playerNativeRefreshSec)
        assertEquals(3, defaults.playerUpNextFirstIndex)
        assertEquals(8, defaults.playerUpNextInterval)
        assertEquals(2, defaults.playerUpNextMax)
        assertEquals(5L, defaults.playerPauseDelaySec)
        assertEquals(3, defaults.playerPauseSessionCap)

        fun parse(vararg values: Pair<String, String>) =
            AdsSettings.parse(defaults, allowUnitIdOverrides = true) { mapOf(*values)[it] }
        assertEquals(30L, parse(AdsKeys.PLAYER_NATIVE_REFRESH_SEC to "10").playerNativeRefreshSec)
        assertEquals(0L, parse(AdsKeys.PLAYER_NATIVE_REFRESH_SEC to "0").playerNativeRefreshSec)
        assertEquals(120L, parse(AdsKeys.PLAYER_NATIVE_REFRESH_SEC to "120").playerNativeRefreshSec)
        assertEquals(2, parse(AdsKeys.PLAYER_UP_NEXT_FIRST_INDEX to "1").playerUpNextFirstIndex)
        assertEquals(1, parse(AdsKeys.PLAYER_UP_NEXT_INTERVAL to "0").playerUpNextInterval)
        assertFalse(parse(AdsKeys.PLAYER_RESPECT_GRACE to "false").playerRespectGrace)
        assertEquals(700, parse(AdsKeys.PLAYER_MIN_HEIGHT_DP to "700").playerMinHeightDp)
        assertEquals(3, parse(AdsKeys.PLAYER_PAUSE_SESSION_CAP to "-1").playerPauseSessionCap)
        val enabled = parse(AdsKeys.PLAYER_UP_NEXT_MAX to "1", AdsKeys.PLACEMENTS_ENABLED to """{"player_up_next":true}""")
        assertTrue(enabled.isEnabled(AdPlacement.PlayerUpNext))
        assertFalse(enabled.isEnabled(AdPlacement.PlayerNowPlaying))
        assertEquals(1, enabled.playerUpNextMax)
    }

    // Policy.

    @Test
    fun `allowed when every rule passes`() {
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerUpNext, tall.copy(upNextExpanded = true)))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerPause, paused))
    }

    @Test
    fun `premium, kill switch, placement switch, consent and ad free block every player placement`() {
        val all = listOf(AdPlacement.PlayerNowPlaying, AdPlacement.PlayerUpNext, AdPlacement.PlayerPause)
        for (p in all) {
            val ctx = paused.copy(upNextExpanded = p == AdPlacement.PlayerUpNext)
            assertEquals(Decision.Block("placement_disabled"), decide(p, ctx, defaults))
            assertEquals(Decision.Block("kill_switch"), decide(p, ctx, on.copy(adsEnabled = false)))
            assertEquals(Decision.Block("no_consent"), decide(p, ctx.copy(canRequestAds = false)))
            store.adFreeUntil = now + 1
            assertEquals(Decision.Block("ad_free"), decide(p, ctx))
            store.adFreeUntil = 0
            premium = true
            assertEquals(Decision.Block(AdFreeEntitlement.REASON_PREMIUM), decide(p, ctx))
            premium = false
        }
    }

    @Test
    fun `grace period and first session, unless ads_player_respect_grace is off`() {
        store.install = now - 23 * AdPolicy.HOUR_MS
        assertEquals(Decision.Block("grace"), decide(AdPlacement.PlayerNowPlaying))
        store.install = now - 25 * AdPolicy.HOUR_MS
        store.sessionCount = 1
        assertEquals(Decision.Block("first_session"), decide(AdPlacement.PlayerNowPlaying))
        val noGrace = on.copy(playerRespectGrace = false)
        store.install = now
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying, s = noGrace))
    }

    @Test
    fun `up next only while fully open`() {
        assertEquals(Decision.Block("up_next_collapsed"), decide(AdPlacement.PlayerUpNext))
        // The Player layout rules do not apply to the list (full sheet in landscape).
        val landscape = tall.copy(upNextExpanded = true, isCompactHeight = true, windowHeightDp = 400)
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerUpNext, landscape))
    }

    @Test
    fun `strip and pause card hidden on short or compact screens, accessibility and errors`() {
        for (p in listOf(AdPlacement.PlayerNowPlaying, AdPlacement.PlayerPause)) {
            assertEquals(Decision.Block("compact_height"), decide(p, paused.copy(isCompactHeight = true)))
            assertEquals(Decision.Block("short_screen"), decide(p, paused.copy(windowHeightDp = 639)))
            assertEquals(Decision.Block("accessibility"), decide(p, paused.copy(isAccessibilityMode = true)))
            assertEquals(Decision.Block("player_error"), decide(p, paused.copy(hasPlaybackError = true)))
        }
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying, tall.copy(windowHeightDp = 640)))
    }

    @Test
    fun `pause card needs a manual pause of the delay, not live, within cap and gap`() {
        assertEquals(Decision.Block("not_paused"), decide(AdPlacement.PlayerPause, tall))
        assertEquals(Decision.Block("pause_delay"), decide(AdPlacement.PlayerPause, tall.copy(manualPauseMs = 4_999)))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerPause, tall.copy(manualPauseMs = 5_000)))
        assertEquals(Decision.Block("live"), decide(AdPlacement.PlayerPause, paused.copy(isLive = true)))
        // Live keeps the strip.
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying, tall.copy(isLive = true)))

        policy.recordShown(AdPlacement.PlayerPause)
        // Once up, the card stays for this pause.
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerPause, paused.copy(pauseCardShowing = true)))
        now += 4 * AdPolicy.MIN_MS
        assertEquals(Decision.Block("interval"), decide(AdPlacement.PlayerPause, paused))
        now += AdPolicy.MIN_MS
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerPause, paused))
        policy.recordShown(AdPlacement.PlayerPause)
        now += 5 * AdPolicy.MIN_MS
        policy.recordShown(AdPlacement.PlayerPause)
        now += 5 * AdPolicy.MIN_MS
        assertEquals(Decision.Block("session_cap"), decide(AdPlacement.PlayerPause, paused))
    }

    @Test
    fun `other placements are not player placements`() {
        assertEquals(
            Decision.Block("not_player"),
            policy.canShowPlayerInline(AdPlacement.PodcastDetailEpisodes, on, tall),
        )
    }

    // Up next rows.

    private fun upNext(count: Int, s: AdsSettings = on, allowed: Boolean = true) =
        interleaveUpNextSlots((1..count).toList(), s, allowed)
            .mapIndexedNotNull { index, row -> if (row is AdSlotRow) index to row.slotKey else null }

    @Test
    fun `up next rows after item 3 then every 8, at most 2, never last`() {
        assertEquals(listOf(3 to "ad-0", 12 to "ad-1"), upNext(30))
        assertEquals(emptyList<Pair<Int, String>>(), upNext(3))
        assertEquals(listOf(3 to "ad-0"), upNext(4))
        assertEquals(listOf(2, 10), upNextSlotIndices(30, on))
        val rows = interleaveUpNextSlots((1..30).toList(), on, true).filterIsInstance<AdSlotRow>()
        assertEquals("player_up_next/ad-0", rows.first().lazyKey)
    }

    @Test
    fun `up next rows never first, and none when not allowed or off`() {
        assertEquals(listOf(2 to "ad-0", 4 to "ad-1"), upNext(10, on.copy(playerUpNextFirstIndex = 1, playerUpNextInterval = 1)))
        assertEquals(emptyList<Pair<Int, String>>(), upNext(30, allowed = false))
        assertEquals(emptyList<Pair<Int, String>>(), upNext(30, defaults))
        assertEquals(emptyList<Pair<Int, String>>(), upNext(30, on.copy(playerUpNextMax = 0)))
    }

    // Strip, refresh and pause timing.

    @Test
    fun `strip only appears at open or episode change with a loaded ad`() {
        assertTrue(PlayerAdRules.stripVisible(PlayerAdRules.StripMoment.Opened, wasVisible = false, allowed = true, hasAd = true))
        assertTrue(PlayerAdRules.stripVisible(PlayerAdRules.StripMoment.EpisodeChanged, false, true, true))
        assertFalse(PlayerAdRules.stripVisible(PlayerAdRules.StripMoment.Opened, false, true, hasAd = false))
        // A late load does not insert it mid-view; a visible one stays and goes when not allowed.
        assertFalse(PlayerAdRules.stripVisible(PlayerAdRules.StripMoment.Updated, false, true, true))
        assertTrue(PlayerAdRules.stripVisible(PlayerAdRules.StripMoment.Updated, true, true, true))
        assertFalse(PlayerAdRules.stripVisible(PlayerAdRules.StripMoment.Updated, true, allowed = false, hasAd = true))
    }

    @Test
    fun `strip refresh every refresh period, or only with the episode`() {
        assertEquals(60_000L, PlayerAdRules.refreshIntervalMs(on))
        assertEquals(45_000L, PlayerAdRules.refreshDelayMs(15_000, on))
        assertEquals(0L, PlayerAdRules.refreshDelayMs(90_000, on))
        assertNull(PlayerAdRules.refreshDelayMs(null, on))
        val episodeOnly = on.copy(playerNativeRefreshSec = 0)
        assertNull(PlayerAdRules.refreshDelayMs(90_000, episodeOnly))
        assertTrue(PlayerAdRules.refreshOnEpisodeChange(1_000, episodeOnly))
        assertFalse(PlayerAdRules.refreshOnEpisodeChange(59_000, on))
        assertTrue(PlayerAdRules.refreshOnEpisodeChange(60_000, on))
        assertFalse(PlayerAdRules.refreshOnEpisodeChange(null, on))
    }

    @Test
    fun `pause counts from the later of the pause and the Player opening`() {
        assertNull(PlayerAdRules.manualPauseMs(pausedAt = null, playerShownAt = 0, now = 10_000))
        assertEquals(7_000L, PlayerAdRules.manualPauseMs(pausedAt = 3_000, playerShownAt = 1_000, now = 10_000))
        assertEquals(2_000L, PlayerAdRules.manualPauseMs(pausedAt = 3_000, playerShownAt = 8_000, now = 10_000))
        assertEquals(3_000L, PlayerAdRules.pauseCardDueInMs(2_000, on))
        assertEquals(0L, PlayerAdRules.pauseCardDueInMs(9_000, on))
        assertNull(PlayerAdRules.pauseCardDueInMs(null, on))
    }
}
