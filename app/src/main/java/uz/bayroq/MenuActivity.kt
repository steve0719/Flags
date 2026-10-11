package uz.bayroq

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.SoundEffectConstants
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MenuActivity : ComponentActivity() {

    private lateinit var web: WebView

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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0a2f7a"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.textZoom = 100
            overScrollMode = View.OVER_SCROLL_NEVER
            addJavascriptInterface(Bridge(), "Bridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
        Ads.init(this)

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

        // O'yin necha soniya ochiq qolgan (0 bo'lsa, yopiq)
        @JavascriptInterface
        fun remaining(game: String): Int = Prefs.unlockLeft(this@MenuActivity, game)

        // Reklama ko'rsatadi, oxirigacha ko'rilsa o'yinni 24 soatga ochadi
        @JavascriptInterface
        fun watchAd(game: String) {
            if (game != "Bayroq") return
            runOnUiThread {
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
                if (Prefs.unlockLeft(this@MenuActivity, "Bayroq") > 0) {
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
