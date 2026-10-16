package uz.bayroq

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MenuActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var logoView: ImageView? = null
    private var revealed = false
    private val t0 = SystemClock.elapsedRealtime()

    private fun js(code: String) = web.evaluateJavascript(code, null)

    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Thread {
                val data = Avatar.make(this, uri)
                runOnUiThread { if (data != null) js("window.onPhoto('$data')") }
            }.start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#1a7df0")
        window.navigationBarColor = Color.parseColor("#0f3fae")
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            // Sahifa tayyor bo'lguncha deyarli ko'rinmaydi (0 emas, chunki brauzer chizishda davom etishi kerak)
            alpha = 0.01f
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.textZoom = 100
            overScrollMode = View.OVER_SCROLL_NEVER
            addJavascriptInterface(Bridge(), "Bridge")
            loadUrl("file:///android_asset/index.html")
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addLogo(root)
        setContentView(root)
        Ads.init(this)

        // Biror sabab bilan signal kelmasa, 5 soniyadan keyin baribir ko'rsatamiz
        web.postDelayed({ reveal() }, 5000)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.nativeBack?window.nativeBack():false") {
                    if (it != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // Ilovaga qaytilganda (botdan yoki o'yindan): Premium va faol o'yin vaqtini yangilaymiz
    override fun onResume() {
        super.onResume()
        Thread {
            var lst: String? = null
            if (online()) {
                Premium.sync(this)
                PlayTime.sync(this)
                lst = Records.sync(this)
            }
            runOnUiThread {
                js("window.onPremium&&window.onPremium()")
                js("window.onPlay&&window.onPlay()")
                val rc = Prefs.recCnt(this)
                if (rc > 0) js("window.onGameResult&&window.onGameResult('Bayroq',$rc,${Prefs.recMs(this) / 1000.0})")
                if (lst != null) js("window.onGameRating&&window.onGameRating('Bayroq',$lst)")
            }
        }.start()
    }

    // Sahifa tayyor bo'lguncha ekranda oq fon va logotip turadi
    private fun addLogo(root: FrameLayout) {
        try {
            val bmp = assets.open("logo.png").use { BitmapFactory.decodeStream(it) } ?: return
            val maxPx = (420 * resources.displayMetrics.density).toInt()
            val side = minOf((resources.displayMetrics.widthPixels * 0.78f).toInt(), maxPx)
            val iv = ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            root.addView(iv, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
            logoView = iv
        } catch (e: Exception) {
        }
    }

    // Sahifa chizildi: uni to'liq ko'rsatamiz va vaqtinchalik logotipni olib tashlaymiz
    private fun reveal() {
        if (revealed) return
        revealed = true
        web.alpha = 1f
        logoView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        logoView = null
    }

    private fun online(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(ms)
    }

    inner class Bridge {
        @JavascriptInterface
        fun ready() {
            runOnUiThread { reveal() }
        }

        // Ilova ochilganidan beri o'tgan millisekundlar (logotip aynan 7 soniya turishi uchun)
        @JavascriptInterface
        fun elapsed(): Int = (SystemClock.elapsedRealtime() - t0).toInt()

        @JavascriptInterface
        fun needsId(): Boolean = Prefs.id(this@MenuActivity) == null

        @JavascriptInterface
        fun getId(): String = Prefs.id(this@MenuActivity) ?: ""

        @JavascriptInterface
        fun fetchId() {
            Thread {
                val id = if (online()) {
                    try { Api.register() } catch (e: Exception) { null }
                } else null
                runOnUiThread {
                    if (id != null) {
                        Prefs.setId(this@MenuActivity, id)
                        js("window.onIdOk('$id')")
                    } else {
                        js("window.onIdFail()")
                    }
                }
            }.start()
        }

        // ---------- Rank: faol o'yin vaqti ----------
        // Jami faol o'yin vaqti (soniya)
        @JavascriptInterface
        fun playSec(): Int = minOf(Prefs.playTotal(this@MenuActivity), Int.MAX_VALUE.toLong()).toInt()

        // Vaqtni serverga yuboradi va yangilaydi
        @JavascriptInterface
        fun syncPlay() {
            Thread {
                if (online()) PlayTime.sync(this@MenuActivity)
                runOnUiThread { js("window.onPlay&&window.onPlay()") }
            }.start()
        }

        // O'yin rekordini yuboradi va reyting ro'yxatini yangilaydi
        @JavascriptInterface
        fun syncRating() {
            Thread {
                val lst = if (online()) Records.sync(this@MenuActivity) else null
                if (lst != null) runOnUiThread { js("window.onGameRating&&window.onGameRating('Bayroq',$lst)") }
            }.start()
        }

        // Profil ma'lumotlari (reyting jadvali uchun)
        @JavascriptInterface
        fun setProfile(name: String, country: String) {
            Prefs.setProfile(this@MenuActivity, name, country)
        }

        // ---------- Premium ----------
        @JavascriptInterface
        fun isPremium(): Boolean = Prefs.isPremium(this@MenuActivity)

        @JavascriptInterface
        fun premiumLeft(): Int = Prefs.premiumLeft(this@MenuActivity)

        @JavascriptInterface
        fun premiumUntil(): String = Prefs.premiumUntil(this@MenuActivity).toString()

        @JavascriptInterface
        fun syncPremium() {
            Thread {
                if (online()) Premium.sync(this@MenuActivity)
                runOnUiThread { js("window.onPremium&&window.onPremium()") }
            }.start()
        }

        // Premium olish: Telegram botni ochadi (internet shart)
        @JavascriptInterface
        fun openBot() {
            Thread {
                if (!online()) {
                    runOnUiThread { js("window.onBot('offline')") }
                    return@Thread
                }
                var bot = Prefs.bot(this@MenuActivity)
                if (bot.isEmpty()) {
                    Premium.sync(this@MenuActivity)
                    bot = Prefs.bot(this@MenuActivity)
                }
                runOnUiThread {
                    if (bot.isEmpty()) {
                        js("window.onBot('fail')")
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$bot")))
                            js("window.onBot('ok')")
                        } catch (e: Exception) {
                            js("window.onBot('fail')")
                        }
                    }
                }
            }.start()
        }

        // ---------- O'yinni ochish ----------
        // O'yin necha soniya ochiq qolgan (0 bo'lsa, yopiq)
        @JavascriptInterface
        fun remaining(game: String): Int = Prefs.unlockLeft(this@MenuActivity, game)

        // Reklama ko'rsatadi, oxirigacha ko'rilsa o'yinni 24 soatga ochadi
        @JavascriptInterface
        fun watchAd(game: String) {
            if (game != "Bayroq") return
            runOnUiThread {
                if (Prefs.isPremium(this@MenuActivity)) {
                    js("window.onAdResult('$game','ok')")
                    return@runOnUiThread
                }
                if (!online()) {
                    js("window.onAdResult('$game','noconn')")
                    return@runOnUiThread
                }
                Ads.show(this@MenuActivity) { st ->
                    if (st == "ok") Prefs.unlock(this@MenuActivity, game)
                    js("window.onAdResult('$game','$st')")
                }
            }
        }

        @JavascriptInterface
        fun startFlag() {
            runOnUiThread {
                val open = Prefs.isPremium(this@MenuActivity) || Prefs.unlockLeft(this@MenuActivity, "Bayroq") > 0
                if (open) {
                    startActivity(Intent(this@MenuActivity, MainActivity::class.java))
                }
            }
        }

        @JavascriptInterface
        fun pickPhoto() {
            runOnUiThread { photoPicker.launch("image/*") }
        }

        @JavascriptInterface
        fun setPrefs(lang: String, sound: Boolean, vib: Boolean) {
            Prefs.set(this@MenuActivity, lang, sound, vib)
        }

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ID", text))
            }
        }

        @JavascriptInterface
        fun tap() {
            web.post {
                if (Prefs.sound(this@MenuActivity)) web.playSoundEffect(SoundEffectConstants.CLICK)
                if (Prefs.vib(this@MenuActivity)) vibrate(20)
            }
        }
    }
}
