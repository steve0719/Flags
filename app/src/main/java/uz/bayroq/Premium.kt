package uz.bayroq

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Premium {
    // Serverdan Premium holatini oladi va telefonga saqlaydi.
    // Natija: true (yangilandi) yoki false (internet yo'q / xato)
    fun sync(c: Context): Boolean {
        val id = Prefs.id(c) ?: return false
        if (Api.BASE.isEmpty()) return false
        return try {
            val conn = URL(Api.BASE + "/status.php").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.outputStream.use { it.write(("id=" + id).toByteArray()) }
            if (conn.responseCode != 200) {
                conn.disconnect()
                false
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val j = JSONObject(body)
                if (!j.optBoolean("ok")) {
                    false
                } else {
                    Prefs.setPremium(c, j.optLong("premium_until"), j.optLong("now"))
                    val bot = j.optString("bot")
                    if (bot.isNotEmpty()) Prefs.setBot(c, bot)
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
