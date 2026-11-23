package com.example.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.AdMobConfig
import com.example.data.preferences.LocalLocalizedContext
import com.google.android.gms.ads.AdSize

/**
 * Lifecycle-aware Google AdMob Banner Ad composable.
 *
 * Displays Google's test banner ad (`ca-app-pub-3940256099942544/6300978111`) during development
 * or switches automatically to the configured production Ad Unit ID when `config.useTestAds == false`.
 *
 * Lifecycle & Leak Prevention:
 * - Observes `LocalLifecycleOwner` for `ON_RESUME`, `ON_PAUSE`, and `ON_DESTROY`.
 * - Detaches listeners, removes the `AdView` from its parent `ViewGroup`, pauses, and calls
 *   `destroy()` idempotently in both `DisposableEffect.onDispose` and `AndroidView.onRelease`.
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    config: AdMobConfig = AdMobConfig(useTestAds = true),
    adUnitId: String? = null,
    adaptive: Boolean = true
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current
    val isInInspection = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val resolvedAdUnitId = remember(adUnitId, config, context) {
        adUnitId?.trim()?.takeIf { it.isNotEmpty() }
            ?: AdMobManager.resolveBannerAdUnitId(context, config)
    }
    val isTestAd = remember(resolvedAdUnitId, config) {
        config.useTestAds || AdMobManager.isUsingTestAdUnit(resolvedAdUnitId)
    }

    if (isInInspection) {
        BannerFallbackCard(
            label = localizedContext.getString(R.string.ad_label),
            subtitle = "AdMob Test Banner ($resolvedAdUnitId)",
            modifier = modifier
                .testTag("banner_ad")
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("banner_ad")
    ) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
        val bannerAdSize = remember(widthDp, adaptive, context) {
            if (adaptive) {
                AdMobManager.resolveAdaptiveBannerAdSize(context, widthDp)
            } else {
                AdSize.BANNER
            }
        }
        val minBannerHeightDp = remember(bannerAdSize) {
            bannerAdSize.height.takeIf { it > 0 }?.dp ?: 52.dp
        }

        var adLoaded by remember(resolvedAdUnitId, bannerAdSize) { mutableStateOf(false) }

        val adView = remember(resolvedAdUnitId, bannerAdSize) {
            AdMobManager.createBannerAdView(
                context = context,
                adUnitId = resolvedAdUnitId,
                adSize = bannerAdSize,
                onAdLoaded = { adLoaded = true },
                onAdFailedToLoad = { adLoaded = false }
            )
        }

        DisposableEffect(lifecycleOwner, adView) {
            val view = adView
            if (view == null) {
                onDispose { }
            } else {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            try {
                                view.resume()
                            } catch (_: Throwable) {
                            }
                        }

                        Lifecycle.Event.ON_PAUSE -> {
                            try {
                                view.pause()
                            } catch (_: Throwable) {
                            }
                        }

                        Lifecycle.Event.ON_DESTROY -> {
                            AdMobManager.releaseAdView(view)
                        }

                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    AdMobManager.releaseAdView(view)
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admob_banner_slot"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedContext.getString(R.string.ad_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isTestAd) "TEST AD" else "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (adView != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minBannerHeightDp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!adLoaded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "KYAATHI Partner Ad Slot",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        AndroidView(
                            factory = { adView },
                            onRelease = { view ->
                                AdMobManager.releaseAdView(view)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("banner_ad_view")
                        )
                    }
                } else {
                    BannerFallbackCard(
                        label = localizedContext.getString(R.string.ad_label),
                        subtitle = "AdMob Banner ($resolvedAdUnitId)"
                    )
                }
            }
        }
    }
}

@Composable
fun AdMobBannerSlot(
    config: AdMobConfig,
    modifier: Modifier = Modifier
) {
    BannerAd(
        modifier = modifier,
        config = config
    )
}

@Composable
private fun BannerFallbackCard(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$label · $subtitle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
