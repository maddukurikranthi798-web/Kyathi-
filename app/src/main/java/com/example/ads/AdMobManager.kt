package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.R
import com.example.data.model.AdMobConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Centralized Google AdMob integration utility for KYAATHI.
 *
 * Handles:
 * - Thread-safe, one-time MobileAds SDK initialization using applicationContext
 * - Test vs. production Ad Unit ID resolution (Banner & Rewarded)
 * - Adaptive Banner AdSize calculation
 * - Safe AdView creation and idempotent lifecycle cleanup (to prevent Activity/View memory leaks)
 */
object AdMobManager {
    private const val TAG = "AdMobManager"

    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    @Volatile
    private var initialized = false

    // Weak-key set tracks destroyed AdViews without retaining View/Context references.
    private val releasedAdViews: MutableSet<AdView> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    // Stateless listener used on release so Compose callbacks are detached without passing null.
    private val noOpAdListener = object : AdListener() {}

    fun isInitialized(): Boolean = initialized

    fun initializeSafely(context: Context): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            return try {
                val appContext = context.applicationContext ?: context
                MobileAds.initialize(appContext) {
                    Log.d(TAG, "Google Mobile Ads SDK initialized")
                }
                initialized = true
                true
            } catch (e: Throwable) {
                Log.w(TAG, "MobileAds.initialize skipped or unavailable: ${e.message}")
                false
            }
        }
    }

    fun resolveBannerAdUnitId(
        context: Context,
        config: AdMobConfig = AdMobConfig(useTestAds = true)
    ): String {
        val testId = try {
            context.getString(R.string.admob_banner_test_id)
        } catch (_: Throwable) {
            TEST_BANNER_AD_UNIT_ID
        }
        return if (!config.useTestAds && config.bannerAdUnitId.isNotBlank()) {
            config.bannerAdUnitId.trim()
        } else {
            testId
        }
    }

    fun resolveRewardedAdUnitId(
        context: Context,
        config: AdMobConfig = AdMobConfig(useTestAds = true)
    ): String {
        val testId = try {
            context.getString(R.string.admob_rewarded_test_id)
        } catch (_: Throwable) {
            TEST_REWARDED_AD_UNIT_ID
        }
        return if (!config.useTestAds && config.rewardedAdUnitId.isNotBlank()) {
            config.rewardedAdUnitId.trim()
        } else {
            testId
        }
    }

    fun isUsingTestAdUnit(adUnitId: String): Boolean {
        val normalized = adUnitId.trim()
        return normalized == TEST_BANNER_AD_UNIT_ID || normalized == TEST_REWARDED_AD_UNIT_ID
    }

    fun buildAdRequest(): AdRequest = AdRequest.Builder().build()

    fun resolveAdaptiveBannerAdSize(context: Context, containerWidthDp: Int): AdSize {
        val clampedWidthDp = containerWidthDp.coerceAtLeast(320)
        return try {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, clampedWidthDp)
        } catch (e: Throwable) {
            Log.w(TAG, "Adaptive AdSize unavailable, falling back to BANNER: ${e.message}")
            AdSize.BANNER
        }
    }

    fun createBannerAdView(
        context: Context,
        adUnitId: String,
        adSize: AdSize,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (String) -> Unit = {}
    ): AdView? {
        return try {
            initializeSafely(context)
            AdView(context).apply {
                setAdSize(adSize)
                this.adUnitId = adUnitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        if (!releasedAdViews.contains(this@apply)) {
                            onAdLoaded()
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Banner failed to load: ${error.message}")
                        if (!releasedAdViews.contains(this@apply)) {
                            onAdFailedToLoad(error.message)
                        }
                    }
                }
                loadAd(buildAdRequest())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AdView creation failed: ${e.message}")
            null
        }
    }

    /**
     * Safely pauses, detaches from any parent ViewGroup, clears listeners, and destroys [adView]
     * at most once to guarantee zero View/Activity/WebView leaks.
     */
    fun releaseAdView(adView: AdView?) {
        if (adView == null) return
        if (!releasedAdViews.add(adView)) return

        try {
            adView.adListener = noOpAdListener
        } catch (_: Throwable) {
        }
        try {
            (adView.parent as? ViewGroup)?.removeView(adView)
        } catch (_: Throwable) {
        }
        try {
            adView.pause()
        } catch (_: Throwable) {
        }
        try {
            adView.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "AdView destroy failed: ${e.message}")
        }
    }

    fun isAdViewReleased(adView: AdView): Boolean = releasedAdViews.contains(adView)

    tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}

class RewardedAdController(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private var rewardedAd: RewardedAd? = null
    private var loadedAdUnitId: String? = null
    private var isReleased = false

    var isLoading by mutableStateOf(false)
        private set
    var isAdReady by mutableStateOf(false)
        private set

    fun preload(adUnitId: String) {
        if (isReleased || isLoading || (rewardedAd != null && loadedAdUnitId == adUnitId)) return
        isLoading = true
        try {
            AdMobManager.initializeSafely(appContext)
            val selfRef = WeakReference(this)
            RewardedAd.load(
                appContext,
                adUnitId,
                AdMobManager.buildAdRequest(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        val controller = selfRef.get() ?: return
                        if (controller.isReleased) {
                            ad.fullScreenContentCallback = null
                            return
                        }
                        controller.rewardedAd = ad
                        controller.loadedAdUnitId = adUnitId
                        controller.isLoading = false
                        controller.isAdReady = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("AdMobManager", "RewardedAd failed to load: ${error.message}")
                        val controller = selfRef.get() ?: return
                        controller.rewardedAd = null
                        controller.isLoading = false
                        controller.isAdReady = false
                    }
                }
            )
        } catch (e: Throwable) {
            Log.w("AdMobManager", "RewardedAd load error: ${e.message}")
            isLoading = false
            isAdReady = false
        }
    }

    fun show(
        activity: Activity,
        adUnitId: String,
        onRewardEarned: (amount: Int, type: String) -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null || isReleased) {
            preload(adUnitId)
            onUnavailable()
            return
        }
        val selfRef = WeakReference(this)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad.fullScreenContentCallback = null
                val controller = selfRef.get() ?: return
                controller.rewardedAd = null
                controller.isAdReady = false
                controller.preload(adUnitId)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w("AdMobManager", "RewardedAd failed to show: ${error.message}")
                ad.fullScreenContentCallback = null
                val controller = selfRef.get() ?: return
                controller.rewardedAd = null
                controller.isAdReady = false
                controller.preload(adUnitId)
                onUnavailable()
            }
        }
        ad.show(activity) { rewardItem ->
            onRewardEarned(rewardItem.amount, rewardItem.type.ifBlank { "Points" })
        }
    }

    fun release() {
        isReleased = true
        try {
            rewardedAd?.fullScreenContentCallback = null
        } catch (_: Throwable) {
        }
        rewardedAd = null
        loadedAdUnitId = null
        isLoading = false
        isAdReady = false
    }
}
