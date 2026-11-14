package tss.t.shared.ads

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of Android `ads/src/test/java/tss/t/ads/PlayerAdsTest.kt` (player-ads-and-premium.md §3):
 * same cases, same reasons. Premium is checked by the iOS caller (`AdsController.canLoadAds`).
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
    private lateinit var store: FakeStore
    private lateinit var policy: AdPolicy

    private val utc = object : AdClock {
        override fun nowMillis() = now
        override fun utcOffsetMillis(atMillis: Long) = 0L
    }

    private val defaults = AdsSettings.defaults(debug = false)
    private val on = defaults.copy(
        placementsEnabled = defaults.placementsEnabled +
            (AdPlacement.PlayerNowPlaying to true) +
            (AdPlacement.PlayerUpNext to true) +
            (AdPlacement.PlayerPause to true),
    )

    private val tall = PlayerAdContext(canRequestAds = true, windowHeightDp = 800)
    private val paused = tall.copy(manualPauseMs = 6_000L)

    @BeforeTest
    fun setUp() {
        store = FakeStore().apply { install = day0 - 7 * AdPolicy.DAY_MS }
        policy = AdPolicy(store, utc)
    }

    private fun decide(placement: AdPlacement, ctx: PlayerAdContext = tall, s: AdsSettings = on) =
        policy.canShowPlayerInline(placement, s, ctx)

    @Test
    fun playerPlacementsAreNativeOffByDefaultAndFlaggedAsPlayer() {
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
    fun playerSettingsDefaultsAndParsing() {
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

    @Test
    fun allowedWhenEveryRulePasses() {
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerUpNext, tall.copy(upNextExpanded = true)))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerPause, paused))
    }

    @Test
    fun killSwitchPlacementSwitchConsentAndAdFreeBlockEveryPlayerPlacement() {
        for (p in listOf(AdPlacement.PlayerNowPlaying, AdPlacement.PlayerUpNext, AdPlacement.PlayerPause)) {
            val ctx = paused.copy(upNextExpanded = p == AdPlacement.PlayerUpNext)
            assertEquals(Decision.Block("placement_disabled"), decide(p, ctx, defaults))
            assertEquals(Decision.Block("kill_switch"), decide(p, ctx, on.copy(adsEnabled = false)))
            assertEquals(Decision.Block("no_consent"), decide(p, ctx.copy(canRequestAds = false)))
            store.adFreeUntil = now + 1
            assertEquals(Decision.Block("ad_free"), decide(p, ctx))
            store.adFreeUntil = 0
        }
    }

    @Test
    fun gracePeriodAndFirstSessionUnlessRespectGraceIsOff() {
        store.install = now - 23 * AdPolicy.HOUR_MS
        assertEquals(Decision.Block("grace"), decide(AdPlacement.PlayerNowPlaying))
        store.install = now - 25 * AdPolicy.HOUR_MS
        store.sessionCount = 1
        assertEquals(Decision.Block("first_session"), decide(AdPlacement.PlayerNowPlaying))
        store.install = now
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying, s = on.copy(playerRespectGrace = false)))
    }

    @Test
    fun upNextOnlyWhileFullyOpen() {
        assertEquals(Decision.Block("up_next_collapsed"), decide(AdPlacement.PlayerUpNext))
        val landscape = tall.copy(upNextExpanded = true, isCompactHeight = true, windowHeightDp = 400)
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerUpNext, landscape))
    }

    @Test
    fun stripAndPauseCardHiddenOnShortOrCompactScreensAccessibilityAndErrors() {
        for (p in listOf(AdPlacement.PlayerNowPlaying, AdPlacement.PlayerPause)) {
            assertEquals(Decision.Block("compact_height"), decide(p, paused.copy(isCompactHeight = true)))
            assertEquals(Decision.Block("short_screen"), decide(p, paused.copy(windowHeightDp = 639)))
            assertEquals(Decision.Block("accessibility"), decide(p, paused.copy(isAccessibilityMode = true)))
            assertEquals(Decision.Block("player_error"), decide(p, paused.copy(hasPlaybackError = true)))
        }
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying, tall.copy(windowHeightDp = 640)))
    }

    @Test
    fun pauseCardNeedsAManualPauseOfTheDelayNotLiveWithinCapAndGap() {
        assertEquals(Decision.Block("not_paused"), decide(AdPlacement.PlayerPause, tall))
        assertEquals(Decision.Block("pause_delay"), decide(AdPlacement.PlayerPause, tall.copy(manualPauseMs = 4_999)))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerPause, tall.copy(manualPauseMs = 5_000)))
        assertEquals(Decision.Block("live"), decide(AdPlacement.PlayerPause, paused.copy(isLive = true)))
        assertEquals(Decision.Allow, decide(AdPlacement.PlayerNowPlaying, tall.copy(isLive = true)))

        policy.recordShown(AdPlacement.PlayerPause)
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
    fun otherPlacementsAreNotPlayerPlacements() {
        assertEquals(Decision.Block("not_player"), policy.canShowPlayerInline(AdPlacement.PodcastDetailEpisodes, on, tall))
    }

    @Test
    fun upNextRowsAfterItem3ThenEvery8AtMost2NeverLastNeverFirst() {
        assertEquals(listOf(2, 10), NativeSlots.upNextAfterIndices(30, on))
        assertEquals(emptyList(), NativeSlots.upNextAfterIndices(3, on))
        assertEquals(listOf(2), NativeSlots.upNextAfterIndices(4, on))
        assertEquals(listOf(1, 2), NativeSlots.upNextAfterIndices(10, on.copy(playerUpNextFirstIndex = 1, playerUpNextInterval = 1)))
        assertEquals(emptyList(), NativeSlots.upNextAfterIndices(30, defaults))
        assertEquals(emptyList(), NativeSlots.upNextAfterIndices(30, on.copy(playerUpNextMax = 0)))
    }

    @Test
    fun stripOnlyAppearsAtOpenOrEpisodeChangeWithALoadedAd() {
        assertTrue(PlayerAdRules.stripVisible(PlayerStripMoment.Opened, wasVisible = false, allowed = true, hasAd = true))
        assertTrue(PlayerAdRules.stripVisible(PlayerStripMoment.EpisodeChanged, false, true, true))
        assertFalse(PlayerAdRules.stripVisible(PlayerStripMoment.Opened, false, true, hasAd = false))
        assertFalse(PlayerAdRules.stripVisible(PlayerStripMoment.Updated, false, true, true))
        assertTrue(PlayerAdRules.stripVisible(PlayerStripMoment.Updated, true, true, true))
        assertFalse(PlayerAdRules.stripVisible(PlayerStripMoment.Updated, true, allowed = false, hasAd = true))
    }

    @Test
    fun stripRefreshEveryPeriodOrOnlyWithTheEpisode() {
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
    fun pauseCountsFromTheLaterOfThePauseAndThePlayerOpening() {
        assertNull(PlayerAdRules.manualPauseMs(pausedAt = null, playerShownAt = 0, now = 10_000))
        assertEquals(7_000L, PlayerAdRules.manualPauseMs(pausedAt = 3_000, playerShownAt = 1_000, now = 10_000))
        assertEquals(2_000L, PlayerAdRules.manualPauseMs(pausedAt = 3_000, playerShownAt = 8_000, now = 10_000))
        assertEquals(3_000L, PlayerAdRules.pauseCardDueInMs(2_000, on))
        assertEquals(0L, PlayerAdRules.pauseCardDueInMs(9_000, on))
        assertNull(PlayerAdRules.pauseCardDueInMs(null, on))
    }
}
