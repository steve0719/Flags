package uz.bayroq

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** O'yin rekordini serverga yuboradi va boshqa o'yinchilar ro'yxatini oladi. */
object Records {
    // Muvaffaqiyatli bo'lsa, o'yinchilar ro'yxatini JSON massiv matni sifatida qaytaradi
    fun sync(c: Context, game: String = "Bayroq"): String? {
        val id = Prefs.id(c) ?: return null
        if (Api.BASE.isEmpty()) return null
        return try {
            val conn = URL(Api.BASE + "/record.php").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            val body = "id=" + id +
                "&game=" + game +
                "&cnt=" + Prefs.recCnt(c) +
                "&ms=" + Prefs.recMs(c)
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) {
                conn.disconnect()
                null
            } else {
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val j = JSONObject(txt)
                if (j.optBoolean("ok")) j.getJSONArray("list").toString() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
