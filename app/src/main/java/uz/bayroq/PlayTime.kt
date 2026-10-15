package uz.bayroq

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Faol o'yin vaqtini hisoblaydi. Har bir o'yin ekrani (Activity) shu sinfdan foydalanadi:
 *   onCreate: clock = PlayTime(this)
 *   onResume: clock.resume()      onPause: clock.pause()
 *   dispatchTouchEvent: clock.touch()
 * Vaqt faqat ekran ochiq va foydalanuvchi faol bo'lganda (oxirgi teginishdan IDLE_MS ichida) hisoblanadi.
 * Telefon soati emas, tizimning bir tekis ishlaydigan taymeri ishlatiladi (soatni surib aldab bo'lmaydi).
 */
class PlayTime(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastTick = 0L
    private var lastTouch = 0L
    private var acc = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            handler.postDelayed(this, 1000)
        }
    }

    private fun step() {
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTick
        lastTick = now
        if (now - lastTouch <= IDLE_MS && dt in 0..5000) acc += dt
        val s = (acc / 1000).toInt()
        if (s > 0) {
            acc -= s * 1000L
            Prefs.addPlay(ctx, s)
        }
    }

    fun resume() {
        if (running) return
        running = true
        val now = SystemClock.elapsedRealtime()
        lastTick = now
        lastTouch = now
        handler.postDelayed(tick, 1000)
    }

    fun pause() {
        if (!running) return
        step()
        running = false
        handler.removeCallbacks(tick)
    }

    // Foydalanuvchi ekranga tegdi (faol)
    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    companion object {
        // Shuncha vaqt hech narsaga tegilmasa, hisob to'xtaydi
        const val IDLE_MS = 45_000L

        // Vaqtni serverga yuboradi. Internet yo'q bo'lsa, vaqt telefonda saqlanib turadi.
        fun sync(c: Context): Boolean {
            val id = Prefs.id(c) ?: return false
            if (Api.BASE.isEmpty()) return false
            val batch = Prefs.playBatch(c)
            return try {
                val conn = URL(Api.BASE + "/playsync.php").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                val body = "id=" + id +
                    "&seq=" + batch.first +
                    "&delta=" + batch.second +
                    "&name=" + URLEncoder.encode(Prefs.pname(c), "UTF-8") +
                    "&country=" + URLEncoder.encode(Prefs.pcountry(c), "UTF-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    false
                } else {
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val j = JSONObject(txt)
                    if (!j.optBoolean("ok")) {
                        false
                    } else {
                        Prefs.playAck(c, j.optLong("play_sec"), j.optInt("play_seq"))
                        true
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
