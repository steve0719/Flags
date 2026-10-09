package uz.bayroq

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

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

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else resultText.apply {
            text = "Kamera ruxsati kerak. Sozlamalardan ruxsat bering."
            visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
        startRound()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        overlay = FlagOverlay(this)
        root.addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(12))
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        timerText = TextView(this).apply {
            textSize = 40f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        scoreText = TextView(this).apply {
            textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.END
        }
        top.addView(timerText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(scoreText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#99000000"))
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
                    isAllCaps = false; textSize = 16f; setTextColor(Color.BLACK)
                    background = rounded(Color.WHITE)
                }
                optionBtns.add(b)
                row.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            optionsBox.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        bottom.addView(optionsBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        nextBtn = Button(this).apply {
            text = "KEYINGISI"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = rounded(Color.parseColor("#2E7D32"), 20)
            visibility = View.GONE
            setOnClickListener { startRound() }
        }
        bottom.addView(nextBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(72)).apply { topMargin = dp(8) })
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
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
            b.background = rounded(Color.WHITE)
            b.setOnClickListener { answer(choices[i], b) }
        }
        optionsBox.visibility = View.VISIBLE
        timer = object : CountDownTimer(10_000, 100) {
            override fun onTick(ms: Long) { timerText.text = ((ms + 999) / 1000).toString() }
            override fun onFinish() { timerText.text = "0"; finishRound(null) }
        }.start()
    }

    private fun answer(choice: Country, btn: Button) {
        if (state != State.PLAY) return
        timer?.cancel()
        finishRound(choice)
        if (choice != target) btn.background = rounded(Color.parseColor("#EF9A9A"))
    }

    private fun finishRound(choice: Country?) {
        if (state == State.DONE) return
        state = State.DONE
        total++
        optionBtns.forEach { b ->
            b.isEnabled = false
            if (b.text == target.name) b.background = rounded(Color.parseColor("#A5D6A7"))
        }
        overlay.flag = target.flag
        resultText.text = when {
            choice == null -> "Vaqt tugadi! Bu: ${target.flag} ${target.name}"
            choice == target -> { score++; "To'g'ri! ${target.flag} ${target.name}" }
            else -> "Noto'g'ri! Bu: ${target.flag} ${target.name}"
        }
        resultText.visibility = View.VISIBLE
        scoreText.text = "Ball: $score/$total"
        nextBtn.visibility = View.VISIBLE
    }

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
