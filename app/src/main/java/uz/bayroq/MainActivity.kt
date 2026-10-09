package uz.bayroq

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

// ====== SOZLAMALAR ======
const val CREATOR_NAME = "Abdimamatov Jamshid"
const val TG_URL = "https://t.me/steve_empire"
const val IG_URL = "https://www.instagram.com/_abdimamatov"
// Test reklama ID (haqiqiy AdMob ID bilan keyin almashtiriladi)
const val AD_UNIT = "ca-app-pub-3940256099942544/1033173712"
const val ADS_EVERY = 20
// =========================

class MainActivity : ComponentActivity() {

    private enum class State { SHUFFLE, PLAY, DONE }

    private lateinit var preview: PreviewView
    private lateinit var overlay: FlagOverlay
    private lateinit var timerText: TextView
    private lateinit var scoreText: TextView
    private lateinit var resultText: TextView
    private lateinit var optionsBox: LinearLayout
    private lateinit var nextBtn: Button
    private val optionBtns = mutableListOf<Button>()

    private val handler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f).build()
    )

    private var state = State.SHUFFLE
    private var target = COUNTRIES[0]
    private var timer: CountDownTimer? = null
    private var shuffleRunnable: Runnable? = null
    private var score = 0
    private var total = 0
    private var interstitial: InterstitialAd? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else resultText.apply {
            text = "Kamera ruxsati kerak. Sozlamalardan ruxsat bering."
            visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        MobileAds.initialize(this) {}
        loadAd()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
        startRound()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    private fun styleBtn(b: Button) { b.stateListAnimator = null; b.minHeight = 0; b.minimumHeight = 0 }

    // ---------- Internet va reklama ----------
    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadAd() {
        if (!isOnline()) return
        InterstitialAd.load(this, AD_UNIT, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
            })
    }

    private fun showAdThen(next: () -> Unit) {
        if (!isOnline()) { next(); return }
        val ad = interstitial
        if (ad == null) { loadAd(); next(); return }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadAd(); next() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { loadAd(); next() }
        }
        ad.show(this)
    }

    // ---------- Interfeys ----------
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        overlay = FlagOverlay(this)
        root.addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(36), dp(18), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val info = TextView(this).apply {
            text = "!"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#55000000")); setStroke(dp(2), Color.WHITE)
            }
            setOnClickListener { showCreator() }
        }
        left.addView(info, LinearLayout.LayoutParams(dp(34), dp(34)))
        timerText = TextView(this).apply {
            textSize = 44f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        left.addView(timerText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        top.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val restart = Button(this).apply {
            text = "RESTART"; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.parseColor("#FF7043"), 20)
            setPadding(dp(16), dp(8), dp(16), dp(8)); styleBtn(this)
            setOnClickListener { restartGame() }
        }
        right.addView(restart, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        scoreText = TextView(this).apply {
            textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, 0)
        }
        right.addView(scoreText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        top.addView(right, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE15151C"))
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        resultText = TextView(this).apply {
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; visibility = View.GONE
            setPadding(0, 0, 0, dp(10))
        }
        bottom.addView(resultText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        optionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val b = Button(this).apply {
                    isAllCaps = false; textSize = 16f; setTextColor(Color.parseColor("#15151C"))
                    typeface = Typeface.DEFAULT_BOLD
                    background = rounded(Color.WHITE, 16); styleBtn(this)
                }
                optionBtns.add(b)
                row.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            }
            optionsBox.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        bottom.addView(optionsBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        nextBtn = Button(this).apply {
            text = "KEYINGISI"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); styleBtn(this)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#43A047"), Color.parseColor("#00C853"))
            ).apply { cornerRadius = dp(22).toFloat() }
            visibility = View.GONE
            setOnClickListener {
                if (total > 0 && total % ADS_EVERY == 0) showAdThen { startRound() } else startRound()
            }
        }
        bottom.addView(nextBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(72)).apply { topMargin = dp(10) })
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun showCreator() {
        AlertDialog.Builder(this)
            .setTitle("Find the flag")
            .setMessage("Yaratuvchi:\n$CREATOR_NAME")
            .setPositiveButton("Telegram") { _, _ -> open(TG_URL) }
            .setNeutralButton("Instagram") { _, _ -> open(IG_URL) }
            .setNegativeButton("Yopish", null)
            .show()
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    // ---------- O'yin mantig'i ----------
    private fun restartGame() {
        if (!isOnline()) {
            Toast.makeText(this, "Internet bilan kiring", Toast.LENGTH_LONG).show()
            return
        }
        timer?.cancel(); shuffleRunnable?.let { handler.removeCallbacks(it) }
        state = State.DONE
        score = 0; total = 0
        showAdThen { startRound() }
    }

    private fun startRound() {
        timer?.cancel(); shuffleRunnable?.let { handler.removeCallbacks(it) }
        state = State.SHUFFLE
        val prev = target
        do { target = COUNTRIES.random() } while (target == prev)
        resultText.visibility = View.GONE
        nextBtn.visibility = View.GONE
        optionsBox.visibility = View.INVISIBLE
        timerText.text = "10"
        timerText.setTextColor(Color.WHITE)
        scoreText.text = "Ball: $score/$total"

        val start = System.currentTimeMillis()
        shuffleRunnable = object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() - start < 2000) {
                    overlay.flag = COUNTRIES.random().flag
                    handler.postDelayed(this, 70)
                } else beginPlay()
            }
        }.also { handler.post(it) }
    }

    private fun beginPlay() {
        state = State.PLAY
        overlay.flag = target.flag
        val choices = (COUNTRIES.filter { it != target }.shuffled().take(3) + target).shuffled()
        optionBtns.forEachIndexed { i, b ->
            b.text = choices[i].name
            b.isEnabled = true
            b.background = rounded(Color.WHITE, 16)
            b.setOnClickListener { answer(choices[i], b) }
        }
        optionsBox.visibility = View.VISIBLE
        timer = object : CountDownTimer(10_000, 100) {
            override fun onTick(ms: Long) {
                timerText.text = ((ms + 999) / 1000).toString()
                timerText.setTextColor(if (ms <= 3000) Color.parseColor("#FF5252") else Color.WHITE)
            }
            override fun onFinish() { timerText.text = "0"; finishRound(null) }
        }.start()
    }

    private fun answer(choice: Country, btn: Button) {
        if (state != State.PLAY) return
        timer?.cancel()
        finishRound(choice)
        if (choice != target) btn.background = rounded(Color.parseColor("#EF5350"), 16)
    }

    private fun finishRound(choice: Country?) {
        if (state != State.PLAY) return
        state = State.DONE
        total++
        optionBtns.forEach { b ->
            b.isEnabled = false
            if (b.text == target.name) b.background = rounded(Color.parseColor("#66BB6A"), 16)
        }
        overlay.flag = target.flag
        when {
            choice == null -> {
                resultText.text = "Vaqt tugadi! Bu: ${target.flag} ${target.name}"
                resultText.setTextColor(Color.parseColor("#FFCA28"))
            }
            choice == target -> {
                score++
                resultText.text = "To'g'ri! ${target.flag} ${target.name}"
                resultText.setTextColor(Color.parseColor("#69F0AE"))
            }
            else -> {
                resultText.text = "Noto'g'ri! Bu: ${target.flag} ${target.name}"
                resultText.setTextColor(Color.parseColor("#FF8A80"))
            }
        }
        resultText.visibility = View.VISIBLE
        scoreText.text = "Ball: $score/$total"
        nextBtn.visibility = View.VISIBLE
    }

    // ---------- Kamera + yuz aniqlash ----------
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val prev = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                .also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { proxy -> analyze(proxy) }
            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, prev, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val rot = proxy.imageInfo.rotationDegrees
        val w = if (rot % 180 == 0) proxy.width else proxy.height
        val h = if (rot % 180 == 0) proxy.height else proxy.width
        detector.process(InputImage.fromMediaImage(media, rot))
            .addOnSuccessListener { faces ->
                val main = faces.maxByOrNull { it.boundingBox.width() }
                runOnUiThread { overlay.update(main?.boundingBox, w, h) }
            }
            .addOnCompleteListener { proxy.close() }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel(); handler.removeCallbacksAndMessages(null)
        cameraExecutor.shutdown(); detector.close()
    }
}
