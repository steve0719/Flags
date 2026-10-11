package uz.bayroq

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

private const val NAVY = "#0b3fa8"

private val STR = mapOf(
    "uz" to mapOf(
        "restart" to "RESTART", "score" to "Ball", "next" to "KEYINGISI",
        "ok" to "To'g'ri!", "bad" to "Noto'g'ri!", "time" to "Vaqt tugadi!", "its" to "Bu",
        "cam" to "Kamera ruxsati kerak. Sozlamalardan ruxsat bering."
    ),
    "ru" to mapOf(
        "restart" to "ЗАНОВО", "score" to "Счёт", "next" to "ДАЛЕЕ",
        "ok" to "Верно!", "bad" to "Неверно!", "time" to "Время вышло!", "its" to "Это",
        "cam" to "Нужно разрешение камеры. Включите его в настройках."
    ),
    "en" to mapOf(
        "restart" to "RESTART", "score" to "Score", "next" to "NEXT",
        "ok" to "Correct!", "bad" to "Wrong!", "time" to "Time's up!", "its" to "It's",
        "cam" to "Camera permission is required. Enable it in settings."
    )
)

class MainActivity : ComponentActivity() {

    private enum class State { SHUFFLE, PLAY, DONE }

    private lateinit var lang: String
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

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else resultText.apply {
            text = s("cam")
            setTextColor(Color.parseColor(NAVY))
            visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lang = Prefs.lang(this).let { if (STR.containsKey(it)) it else "uz" }
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
        startRound()
    }

    private fun s(k: String): String = STR[lang]?.get(k) ?: STR.getValue("uz").getValue(k)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(c1: String, c2: String, r: Int, stroke: String? = null) =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
        ).apply {
            cornerRadius = dp(r).toFloat()
            if (stroke != null) setStroke(dp(3), Color.parseColor(stroke))
        }

    private fun circle(color: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(color))
        setStroke(dp(4), Color.WHITE)
    }

    private fun styleBtn(b: Button) {
        b.stateListAnimator = null; b.minHeight = 0; b.minimumHeight = 0
    }

    private fun styleOption(b: Button, mode: Int) {
        when (mode) {
            1 -> { b.background = pill("#3ddc5a", "#22b53a", 20, "#15862a"); b.setTextColor(Color.WHITE) }
            2 -> { b.background = pill("#ff7a7a", "#ff4d4d", 20, "#c93636"); b.setTextColor(Color.WHITE) }
            else -> { b.background = pill("#FFFFFF", "#FFFFFF", 20, "#cfe0ff"); b.setTextColor(Color.parseColor(NAVY)) }
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        if (!Prefs.vib(this)) return
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(ms)
    }

    // ---------- Interfeys ----------
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        overlay = FlagOverlay(this)
        root.addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Yuqori panel: orqaga | timer | RESTART + ball
        val top = FrameLayout(this).apply {
            setPadding(dp(14), dp(12), dp(14), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT)
            )
        }
        val back = TextView(this).apply {
            text = "‹"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = circle("#1e90ff")
            setOnClickListener { finish() }
        }
        top.addView(back, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.START or Gravity.TOP))

        timerText = TextView(this).apply {
            textSize = 32f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = circle("#1e90ff")
        }
        top.addView(timerText, FrameLayout.LayoutParams(dp(74), dp(74), Gravity.CENTER_HORIZONTAL or Gravity.TOP))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val restart = Button(this).apply {
            text = s("restart"); textSize = 12f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = pill("#ffb84a", "#ff9f0a", 20, "#cc7a00")
            setPadding(dp(16), 0, dp(16), 0); styleBtn(this)
            setOnClickListener { restartGame() }
        }
        right.addView(restart, LinearLayout.LayoutParams(WRAP_CONTENT, dp(38)))
        scoreText = TextView(this).apply {
            textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, 0)
        }
        right.addView(scoreText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        top.addView(right, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.TOP))
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        // Pastki panel
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        resultText = TextView(this).apply {
            textSize = 20f; setTextColor(Color.parseColor(NAVY)); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; visibility = View.GONE
            setPadding(0, 0, 0, dp(10))
        }
        bottom.addView(resultText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        optionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val b = Button(this).apply {
                    isAllCaps = false; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2; setPadding(dp(8), 0, dp(8), 0); styleBtn(this)
                }
                styleOption(b, 0)
                optionBtns.add(b)
                row.addView(b, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            }
            optionsBox.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        bottom.addView(optionsBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        nextBtn = Button(this).apply {
            text = s("next"); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); styleBtn(this)
            background = pill
