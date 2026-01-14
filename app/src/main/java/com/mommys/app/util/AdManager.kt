package com.mommys.app.util

import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.applovin.sdk.AppLovinSdkUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdManager - Gestiona los anuncios de AppLovin MAX
 * Implementación EXACTA basada en vi.b de la app original
 */
class AdManager(private val activity: Activity) {
    
    companion object {
        // SDK Key de AppLovin (exacto como la app original)
        private const val SDK_KEY = "Da8CiknOSINgIwr_cL_2TuK21qwexDf8v22anyN7UT9SfqI-gb0uAyHknrVyDgbmQeeVKDlGoTaDNbFff1yoAP"
        
        // Ad Unit ID para banners (exacto como la app original)
        private const val BANNER_AD_UNIT_ID = "d1fefdb44d5fa1c1"
        
        // Privacy Policy URL (exacto como la app original)
        private const val PRIVACY_POLICY_URL = "https://zepiwolf.se/tws/privacy-policy/"
        
        // f26758c - Control global de inicialización del SDK (solo una vez)
        // Exactamente como: public static final AtomicBoolean f26758c = new AtomicBoolean(false);
        private val sdkInitialized = AtomicBoolean(false)
    }
    
    // f26760b - Control de si el banner ya está cargándose/cargado
    // Exactamente como: public final AtomicBoolean f26760b = new AtomicBoolean(false);
    private val bannerLoading = AtomicBoolean(false)
    
    /**
     * a(FrameLayout) - Oculta el banner de anuncios
     * Exactamente como vi.b.a() en la app original:
     * 
     * public final void a(FrameLayout frameLayout) {
     *     this.f26760b.set(false);
     *     frameLayout.setVisibility(8);
     *     frameLayout.setMinimumHeight(0);
     * }
     */
    fun hideBannerAd(container: FrameLayout) {
        bannerLoading.set(false)
        container.visibility = View.GONE  // 8 = View.GONE
        container.minimumHeight = 0
    }
    
    /**
     * b() - Inicializa el SDK de AppLovin (solo una vez globalmente)
     * Exactamente como vi.b.b() en la app original:
     * 
     * public final void b() {
     *     if (f26758c.compareAndSet(false, true)) {
     *         // ... inicialización
     *     }
     * }
     */
    fun initializeSdk() {
        if (sdkInitialized.compareAndSet(false, true)) {
            val config = AppLovinSdkInitializationConfiguration.builder(SDK_KEY, activity)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .build()
            
            AppLovinPrivacySettings.setHasUserConsent(true, activity)
            
            val settings = AppLovinSdk.getInstance(activity).settings
            settings.termsAndPrivacyPolicyFlowSettings.isEnabled = true
            settings.termsAndPrivacyPolicyFlowSettings.privacyPolicyUri = Uri.parse(PRIVACY_POLICY_URL)
            
            AppLovinSdk.getInstance(activity).initialize(config) { _ -> }
        }
    }
    
    /**
     * c(FrameLayout, Activity) - Muestra el banner de anuncios
     * Exactamente como vi.b.c() en la app original:
     * 
     * public final void c(FrameLayout frameLayout, j.j jVar) {
     *     if (this.f26760b.compareAndSet(false, true)) {
     *         MaxAdView maxAdView = new MaxAdView("d1fefdb44d5fa1c1", jVar);
     *         // ... configuración
     *         frameLayout.addView(maxAdView);
     *         maxAdView.setListener(new a(this, maxAdView));
     *         maxAdView.loadAd();
     *         maxAdView.startAutoRefresh();
     *     }
     * }
     */
    fun showBannerAd(container: FrameLayout, hostActivity: Activity) {
        if (bannerLoading.compareAndSet(false, true)) {
            // Restaurar visibilidad del container (en caso de que estuviera GONE)
            container.visibility = View.VISIBLE
            container.minimumHeight = AppLovinSdkUtils.dpToPx(activity, 48)
            
            val maxAdView = MaxAdView(BANNER_AD_UNIT_ID, hostActivity)
            
            val adFormat = MaxAdFormat.BANNER
            val heightPx = AppLovinSdkUtils.dpToPx(activity, adFormat.getAdaptiveSize(activity).height)
            
            maxAdView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heightPx
            )
            
            maxAdView.setExtraParameter("adaptive_banner", "true")
            
            container.addView(maxAdView)
            
            // Listener exactamente como vi.a en la app original
            // Solo onAdLoaded actualiza el tamaño, los demás están vacíos
            maxAdView.setListener(object : MaxAdViewAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    // Exactamente como vi.a.onAdLoaded:
                    // this.f26748a.setLayoutParams(new FrameLayout.LayoutParams(-1, 
                    //     AppLovinSdkUtils.dpToPx(this.f26749b.f26759a, maxAd.getSize().getHeight())));
                    maxAdView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        AppLovinSdkUtils.dpToPx(activity, ad.size.height)
                    )
                }
                
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdHidden(ad: MaxAd) {}
                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                override fun onAdExpanded(ad: MaxAd) {}
                override fun onAdCollapsed(ad: MaxAd) {}
            })
            
            maxAdView.loadAd()
            maxAdView.startAutoRefresh()
        }
    }
}
