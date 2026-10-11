package uz.bayroq

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object Ads {
    // Google'ning TEST reklama bloki. Haqiqiy AdMob "Rewarded" blok ID sini
    // keyin shu yerga yozasiz (ilova ID si manifestda turadi).
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private var ad: RewardedAd? = null
    private var loading = false
    private var started = false

    // Menyu ochilganda bir marta chaqiriladi, reklamani oldindan yuklab qo'yadi
    fun init(a: Activity) {
        if (started) return
        started = true
        MobileAds.initialize(a) {}
        preload(a)
    }

    private fun preload(a: Activity) {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(a, REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(r: RewardedAd) { ad = r; loading = false }
            override fun onAdFailedToLoad(e: LoadAdError) { ad = null; loading = false }
        })
    }

    // Natija: "ok" (oxirigacha ko'rildi), "skip" (oxirigacha ko'rilmadi), "fail" (reklama yo'q)
    fun show(a: Activity, done: (String) -> Unit) {
        val ready = ad
        if (ready != null) {
            ad = null
            present(a, ready, done)
            return
        }
        // Oldindan yuklanmagan bo'lsa, hozir yuklab ko'ramiz
        RewardedAd.load(a, REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(r: RewardedAd) { present(a, r, done) }
            override fun onAdFailedToLoad(e: LoadAdError) { done("fail") }
        })
    }

    private fun present(a: Activity, r: RewardedAd, done: (String) -> Unit) {
        var earned = false
        r.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(a)
                done(if (earned) "ok" else "skip")
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                preload(a)
                done("fail")
            }
        }
        r.show(a) { earned = true }
    }
}
