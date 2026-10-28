package tss.t.ads

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class AdPolicyTest {

    private class FakeStore : AdCounterStore {
        var install = 0L
        override fun installAt(now: Long) = install
        override var sessionCount = 5
        override var lastFullScreenAt = 0L
        override var adFreeUntil = 0L
        val last = mutableMapOf<AdPlacement, Long>()
        val daily = mutableMapOf<Pair<AdPlacement, Long>, Int>()
        val session = mutableMapOf<AdPlacement, Int>()
        override fun lastShownAt(placement: AdPlacement) = last[placement] ?: 0L
        override fun shownOnDay(placement: AdPlacement, day: Long) = daily[placement to day] ?: 0
        override fun shownThisSession(placement: AdPlacement) = session[placement] ?: 0
        override fun recordShown(placement: AdPlacement, now: Long, day: Long) {
            last[placement] = now
            daily[placement to day] = shownOnDay(placement, day) + 1
            session[placement] = shownThisSession(placement) + 1
            if (placement.isFullScreen) lastFullScreenAt = now
        }

        override fun startSession() {
            sessionCount++
        }

        override fun grantAdFreeUntil(until: Long) {
            adFreeUntil = until
        }
    }

    private val day0 = 1_800_000_000_000L // 2027-01-15, any fixed instant
    private var now = day0
    private lateinit var store: FakeStore
    private lateinit var policy: AdPolicy

    private val release = AdsSettings.defaults(debug = false).let {
        it.copy(
            adsEnabled = true,
            placementsEnabled = it.placementsEnabled +
                    (AdPlacement.AppOpenWarm to true) +
                    (AdPlacement.InterstitialNav to true) +
                    (AdPlacement.RewardedAdFree to true),
        )
    }

    private val okContext = TriggerContext(
        canRequestAds = true,
        playbackActive = false,
        onPlayerScreen = false,
        externalLaunch = false,
        onboardingCompleted = true,
        backgroundDurationMs = 5 * 60_000L,
    )

    @Before
    fun setUp() {
        store = FakeStore().apply { install = day0 - 7 * AdPolicy.DAY_MS }
        policy = AdPolicy(store, clock = { now }, timeZone = { TimeZone.getTimeZone("UTC") })
    }

    private fun appOpen(ctx: TriggerContext = okContext, settings: AdsSettings = release) =
        policy.canShowFullScreen(AdPlacement.AppOpenWarm, settings, ctx)

    @Test
    fun `app open allowed when every rule passes`() {
        assertEquals(Decision.Allow, appOpen())
    }

    @Test
    fun `kill switch and placement switch block`() {
        assertEquals(Decision.Block("kill_switch"), appOpen(settings = release.copy(adsEnabled = false)))
        val off = release.copy(
            placementsEnabled = release.placementsEnabled + (AdPlacement.AppOpenWarm to false)
        )
        assertEquals(Decision.Block("placement_disabled"), appOpen(settings = off))
    }

    @Test
    fun `release defaults keep app open and interstitial off`() {
        val defaults = AdsSettings.defaults(debug = false).copy(adsEnabled = true)
        assertEquals(Decision.Block("placement_disabled"), appOpen(settings = defaults))
        assertEquals(
            Decision.Block("placement_disabled"),
            policy.canShowFullScreen(AdPlacement.InterstitialNav, defaults, okContext)
        )
        assertEquals(false, AdsSettings.defaults(debug = false).adsEnabled)
    }

    @Test
    fun `no consent blocks`() {
        assertEquals(Decision.Block("no_consent"), appOpen(okContext.copy(canRequestAds = false)))
    }

    @Test
    fun `audio first - never while playing, on the player or from a notification`() {
        assertEquals(Decision.Block("audio_playing"), appOpen(okContext.copy(playbackActive = true)))
        assertEquals(Decision.Block("player_screen"), appOpen(okContext.copy(onPlayerScreen = true)))
        assertEquals(Decision.Block("external_launch"), appOpen(okContext.copy(externalLaunch = true)))
        assertEquals(
            Decision.Block("audio_playing"),
            policy.canShowFullScreen(
                AdPlacement.InterstitialNav, release, okContext.copy(playbackActive = true)
            )
        )
    }

    @Test
    fun `grace period after install`() {
        store.install = now - AdPolicy.HOUR_MS
        assertEquals(Decision.Block("grace"), appOpen())
        store.install = now - 25 * AdPolicy.HOUR_MS
        assertEquals(Decision.Allow, appOpen())
    }

    @Test
    fun `onboarding must be finished`() {
        assertEquals(Decision.Block("onboarding"), appOpen(okContext.copy(onboardingCompleted = false)))
    }

    @Test
    fun `short background and first sessions skip app open`() {
        assertEquals(
            Decision.Block("short_background"),
            appOpen(okContext.copy(backgroundDurationMs = 10_000L))
        )
        store.sessionCount = 2
        assertEquals(Decision.Block("early_session"), appOpen())
        store.sessionCount = 3
        assertEquals(Decision.Allow, appOpen())
    }

    @Test
    fun `app open interval, gap and daily cap`() {
        policy.recordShown(AdPlacement.AppOpenWarm)
        now += 30_000L
        assertEquals(Decision.Block("gap"), appOpen())
        now += 30 * AdPolicy.MIN_MS
        assertEquals(Decision.Block("interval"), appOpen())
        now += 31 * AdPolicy.MIN_MS
        assertEquals(Decision.Allow, appOpen())

        policy.recordShown(AdPlacement.AppOpenWarm)
        now += 2 * AdPolicy.HOUR_MS
        policy.recordShown(AdPlacement.AppOpenWarm)
        now += 2 * AdPolicy.HOUR_MS
        // Three shown today (cap 3) unless the day rolled over.
        val sameDay = policy.day(now) == policy.day(day0)
        assertEquals(if (sameDay) Decision.Block("daily_cap") else Decision.Allow, appOpen())
    }

    @Test
    fun `interstitial caps`() {
        val p = AdPlacement.InterstitialNav
        assertEquals(Decision.Allow, policy.canShowFullScreen(p, release, okContext))
        policy.recordShown(p)
        now += 120_000L
        assertEquals(Decision.Block("interval"), policy.canShowFullScreen(p, release, okContext))
        now += 120_000L
        assertEquals(Decision.Allow, policy.canShowFullScreen(p, release, okContext))
        policy.recordShown(p)
        now += 10 * AdPolicy.MIN_MS
        assertEquals(Decision.Block("session_cap"), policy.canShowFullScreen(p, release, okContext))

        store.sessionCount = 1
        assertEquals(Decision.Block("first_session"), policy.canShowFullScreen(p, release, okContext))
    }

    @Test
    fun `rewarded ad-free window blocks other ads but not rewarded`() {
        store.adFreeUntil = now + AdPolicy.HOUR_MS
        assertEquals(Decision.Block("ad_free"), appOpen())
        assertEquals(
            Decision.Block("ad_free"),
            policy.canShowInline(AdPlacement.FavouriteTop, release, canRequestAds = true)
        )
        assertEquals(
            Decision.Allow,
            policy.canShowRewarded(
                AdPlacement.RewardedAdFree, release, okContext.copy(playbackActive = true)
            )
        )
    }

    @Test
    fun `inline needs switches and consent only`() {
        assertEquals(Decision.Allow, policy.canShowInline(AdPlacement.FavouriteTop, release, true))
        assertEquals(
            Decision.Block("no_consent"),
            policy.canShowInline(AdPlacement.FavouriteTop, release, false)
        )
        assertEquals(
            Decision.Block("placement_disabled"),
            policy.canShowInline(AdPlacement.PlayerEpisodeList, release, true)
        )
    }
}
